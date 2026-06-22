package com.offlinevault.security

import com.offlinevault.data.preferences.SecurityPreferences
import kotlinx.coroutines.CancellationException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** Outcome of an unlock attempt. */
sealed interface UnlockResult {
    object Success : UnlockResult
    object WrongCredential : UnlockResult
    data class Delayed(val secondsRemaining: Int) : UnlockResult
    data class Error(val message: String) : UnlockResult
}

/**
 * High level key lifecycle: first-time setup, unlock, recovery, master-password change and the
 * biometric wrapping orchestration. All persistence goes through [SecurityPreferences]; the live
 * key lives only in [SessionManager].
 *
 * Envelope encryption model:
 *   DEK (random 256-bit)  ── wrapped by ──▶  master key  = PBKDF2(masterPassword, masterSalt)
 *                          └─ wrapped by ──▶ recovery key = PBKDF2(recoveryAnswer, recoverySalt)
 * The DEK is never stored in the clear. Master password and recovery answer are never stored at all.
 */
class KeyManager(private val prefs: SecurityPreferences) {

    /** First launch: create the DEK and wrap it under both the master password and recovery answer. */
    suspend fun setup(
        masterPassword: String,
        recoveryQuestion: String,
        recoveryAnswer: String
    ) {
        val dek = CryptoManager.newDataEncryptionKey()

        val masterSalt = CryptoManager.newSalt()
        val recoverySalt = CryptoManager.newSalt()

        val iterations = CryptoManager.DEFAULT_PBKDF2_ITERATIONS
        val masterKey = CryptoManager.deriveKey(masterPassword.toCharArray(), masterSalt, iterations)
        val recoveryKey = CryptoManager.deriveKey(normalizeAnswer(recoveryAnswer).toCharArray(), recoverySalt, iterations)

        val masterWrapped = CryptoManager.encrypt(masterKey, dek.encoded)
        val recoveryWrapped = CryptoManager.encrypt(recoveryKey, dek.encoded)

        prefs.saveVaultMaterial(
            masterSalt = CryptoManager.encode(masterSalt),
            recoverySalt = CryptoManager.encode(recoverySalt),
            masterWrappedDek = CryptoManager.encode(masterWrapped),
            recoveryWrappedDek = CryptoManager.encode(recoveryWrapped),
            recoveryQuestion = recoveryQuestion,
            masterIterations = iterations,
            recoveryIterations = iterations
        )
        prefs.resetFailures()
        SessionManager.unlock(dek)
    }

    /** How many seconds the user must still wait before another attempt is allowed. */
    suspend fun unlockDelaySeconds(): Int {
        return UnlockThrottle.remainingSeconds(
            attempts = prefs.failedAttempts(),
            lastFailedAt = prefs.lastFailedAt(),
            now = System.currentTimeMillis()
        )
    }

    suspend fun unlockWithPassword(masterPassword: String): UnlockResult {
        val delay = unlockDelaySeconds()
        if (delay > 0) return UnlockResult.Delayed(delay)

        val saltB64 = prefs.masterSalt() ?: return UnlockResult.Error("密码库尚未初始化")
        val wrappedB64 = prefs.masterWrappedDek() ?: return UnlockResult.Error("密码库尚未初始化")

        return try {
            val masterKey = CryptoManager.deriveKey(
                masterPassword.toCharArray(),
                CryptoManager.decode(saltB64),
                prefs.masterIterations()
            )
            val dekBytes = CryptoManager.decrypt(masterKey, CryptoManager.decode(wrappedB64))
            prefs.resetFailures()
            SessionManager.unlock(SecretKeySpec(dekBytes, "AES"))
            UnlockResult.Success
        } catch (e: AEADBadTagException) {
            prefs.recordFailure()
            UnlockResult.WrongCredential
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            UnlockResult.Error("无法读取加密密钥，数据可能已损坏")
        }
    }

    /** Recovery: answer the security question, recover the DEK, then set a brand new master password. */
    suspend fun recoverWithAnswer(recoveryAnswer: String, newMasterPassword: String): UnlockResult {
        val delay = unlockDelaySeconds()
        if (delay > 0) return UnlockResult.Delayed(delay)

        val saltB64 = prefs.recoverySalt() ?: return UnlockResult.Error("未设置恢复信息")
        val wrappedB64 = prefs.recoveryWrappedDek() ?: return UnlockResult.Error("未设置恢复信息")

        val dekBytes = try {
            val recoveryKey = CryptoManager.deriveKey(
                normalizeAnswer(recoveryAnswer).toCharArray(),
                CryptoManager.decode(saltB64),
                prefs.recoveryIterations()
            )
            CryptoManager.decrypt(recoveryKey, CryptoManager.decode(wrappedB64))
        } catch (e: CancellationException) {
            throw e
        } catch (_: AEADBadTagException) {
            prefs.recordFailure()
            return UnlockResult.WrongCredential
        } catch (_: Exception) {
            return UnlockResult.Error("无法读取恢复密钥，数据可能已损坏")
        }

        // Re-wrap the recovered DEK under a fresh master password.
        val dek = SecretKeySpec(dekBytes, "AES")
        rewrapMaster(dek, newMasterPassword)
        prefs.resetFailures()
        SessionManager.unlock(dek)
        return UnlockResult.Success
    }

    /** Change master password: verify the old one, then re-wrap the DEK under the new one. */
    suspend fun changeMasterPassword(oldPassword: String, newPassword: String): UnlockResult {
        val delay = unlockDelaySeconds()
        if (delay > 0) return UnlockResult.Delayed(delay)

        val saltB64 = prefs.masterSalt() ?: return UnlockResult.Error("密码库尚未初始化")
        val wrappedB64 = prefs.masterWrappedDek() ?: return UnlockResult.Error("密码库尚未初始化")

        val dekBytes = try {
            val oldKey = CryptoManager.deriveKey(
                oldPassword.toCharArray(),
                CryptoManager.decode(saltB64),
                prefs.masterIterations()
            )
            CryptoManager.decrypt(oldKey, CryptoManager.decode(wrappedB64))
        } catch (e: CancellationException) {
            throw e
        } catch (_: AEADBadTagException) {
            prefs.recordFailure()
            return UnlockResult.WrongCredential
        } catch (_: Exception) {
            return UnlockResult.Error("无法读取加密密钥，数据可能已损坏")
        }
        rewrapMaster(SecretKeySpec(dekBytes, "AES"), newPassword)
        prefs.resetFailures()
        return UnlockResult.Success
    }

    /** Replace the security question / answer. Requires the vault to be currently unlocked. */
    suspend fun changeRecovery(question: String, answer: String) {
        val dek = SessionManager.requireKey()
        val salt = CryptoManager.newSalt()
        val iterations = CryptoManager.DEFAULT_PBKDF2_ITERATIONS
        val recoveryKey = CryptoManager.deriveKey(normalizeAnswer(answer).toCharArray(), salt, iterations)
        val wrapped = CryptoManager.encrypt(recoveryKey, dek.encoded)
        prefs.updateRecoveryMaterial(
            recoverySalt = CryptoManager.encode(salt),
            recoveryWrappedDek = CryptoManager.encode(wrapped),
            recoveryQuestion = question,
            recoveryIterations = iterations
        )
    }

    private suspend fun rewrapMaster(dek: SecretKey, newPassword: String) {
        val salt = CryptoManager.newSalt()
        val iterations = CryptoManager.DEFAULT_PBKDF2_ITERATIONS
        val masterKey = CryptoManager.deriveKey(newPassword.toCharArray(), salt, iterations)
        val wrapped = CryptoManager.encrypt(masterKey, dek.encoded)
        prefs.updateMasterMaterial(
            masterSalt = CryptoManager.encode(salt),
            masterWrappedDek = CryptoManager.encode(wrapped),
            masterIterations = iterations
        )
    }

    // ---- Biometric ---------------------------------------------------------

    /**
     * Persists the DEK wrapped by the biometric-bound Keystore key.
     * [authenticatedCipher] must already have passed through BiometricPrompt in ENCRYPT mode.
     */
    suspend fun storeBiometricWrappedDek(authenticatedCipher: Cipher) {
        val dek = SessionManager.requireKey()
        val ciphertext = authenticatedCipher.doFinal(dek.encoded)
        val blob = KeystoreManager.joinIv(authenticatedCipher.iv, ciphertext)
        prefs.setBiometric(enabled = true, wrappedDek = CryptoManager.encode(blob))
    }

    suspend fun disableBiometric() {
        prefs.setBiometric(enabled = false, wrappedDek = null)
        KeystoreManager.deleteKey()
    }

    /** Builds a decrypt cipher (gated by biometrics) for the stored wrapped DEK, or null if unavailable. */
    suspend fun biometricDecryptCipher(): Cipher? {
        val blobB64 = prefs.biometricWrappedDek() ?: return null
        if (!KeystoreManager.hasKey()) return null
        val (iv, _) = KeystoreManager.splitIv(CryptoManager.decode(blobB64))
        return runCatching { KeystoreManager.decryptCipher(iv) }.getOrNull()
    }

    /** Completes biometric unlock using the authenticated decrypt cipher. */
    suspend fun unlockWithBiometricCipher(authenticatedCipher: Cipher): UnlockResult {
        val blobB64 = prefs.biometricWrappedDek() ?: return UnlockResult.Error("尚未启用指纹解锁")
        return try {
            val (_, ciphertext) = KeystoreManager.splitIv(CryptoManager.decode(blobB64))
            val dekBytes = authenticatedCipher.doFinal(ciphertext)
            prefs.resetFailures()
            SessionManager.unlock(SecretKeySpec(dekBytes, "AES"))
            UnlockResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UnlockResult.Error("指纹解锁失败")
        }
    }

    fun lock() = SessionManager.lock()

    /** Normalises recovery answers so capitalisation / surrounding spaces don't matter. */
    private fun normalizeAnswer(answer: String): String = answer.trim().lowercase()
}
