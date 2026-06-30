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
class KeyManager(
    private val prefs: SecurityPreferences,
    private val mnemonicManager: MnemonicManager
) {

    /** First launch: create the DEK and wrap it under both the master password and recovery answer. */
    suspend fun setup(
        masterPassword: String,
        recoveryQuestion: String,
        recoveryAnswer: String,
        mnemonicPhrase: String
    ) {
        require(recoveryQuestion.isNotBlank()) { "安全问题不能为空" }
        require(recoveryAnswer.isNotBlank()) { "安全问题答案不能为空" }
        val dek = CryptoManager.newDataEncryptionKey()

        val masterSalt = CryptoManager.newSalt()
        val recoverySalt = CryptoManager.newSalt()

        val iterations = CryptoManager.DEFAULT_PBKDF2_ITERATIONS
        val masterKey = CryptoManager.deriveKey(masterPassword.toCharArray(), masterSalt, iterations)
        val recoveryKey = CryptoManager.deriveKey(normalizeAnswer(recoveryAnswer).toCharArray(), recoverySalt, iterations)
        val normalizedMnemonic = mnemonicManager.normalize(mnemonicPhrase)
        require(mnemonicManager.isValidPhrase(normalizedMnemonic)) { "助记词格式无效" }
        val mnemonicSalt = CryptoManager.newSalt()
        val mnemonicKey = mnemonicManager.deriveRecoveryKey(normalizedMnemonic, mnemonicSalt)

        val masterWrapped = wrapDek(masterKey, dek)
        val recoveryWrapped = wrapDek(recoveryKey, dek)
        val mnemonicWrapped = wrapDek(mnemonicKey, dek)
        val mnemonicVerifierHash = mnemonicManager.verifierHash(normalizedMnemonic, mnemonicSalt)

        prefs.saveVaultMaterial(
            masterSalt = CryptoManager.encode(masterSalt),
            recoverySalt = CryptoManager.encode(recoverySalt),
            masterWrappedDek = CryptoManager.encode(masterWrapped),
            recoveryWrappedDek = CryptoManager.encode(recoveryWrapped),
            recoveryQuestion = recoveryQuestion,
            masterIterations = iterations,
            recoveryIterations = iterations,
            mnemonicSalt = CryptoManager.encode(mnemonicSalt),
            mnemonicWrappedDek = CryptoManager.encode(mnemonicWrapped),
            mnemonicVerifierHash = mnemonicVerifierHash
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
            SessionManager.unlock(secretKeyFromBytes(dekBytes))
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
        val dek = secretKeyFromBytes(dekBytes)
        rewrapMaster(dek, newMasterPassword)
        prefs.resetFailures()
        return UnlockResult.Success
    }

    /** Final recovery path: 12-word phrase recovers the DEK and only resets the master password. */
    suspend fun recoverWithMnemonic(mnemonicPhrase: String, newMasterPassword: String): UnlockResult {
        val delay = unlockDelaySeconds()
        if (delay > 0) return UnlockResult.Delayed(delay)

        val saltB64 = prefs.mnemonicSalt() ?: return UnlockResult.Error("未启用助记词恢复")
        val wrappedB64 = prefs.mnemonicWrappedDek() ?: return UnlockResult.Error("未启用助记词恢复")
        val verifierHash = prefs.mnemonicVerifierHash() ?: return UnlockResult.Error("未启用助记词恢复")
        val normalizedMnemonic = mnemonicManager.normalize(mnemonicPhrase)
        if (!mnemonicManager.isValidPhrase(normalizedMnemonic)) {
            prefs.recordFailure()
            return UnlockResult.WrongCredential
        }

        val dekBytes = try {
            val salt = CryptoManager.decode(saltB64)
            if (!mnemonicManager.verifierMatches(normalizedMnemonic, salt, verifierHash)) {
                prefs.recordFailure()
                return UnlockResult.WrongCredential
            }
            val mnemonicKey = mnemonicManager.deriveRecoveryKey(normalizedMnemonic, salt)
            CryptoManager.decrypt(mnemonicKey, CryptoManager.decode(wrappedB64))
        } catch (e: CancellationException) {
            throw e
        } catch (_: AEADBadTagException) {
            prefs.recordFailure()
            return UnlockResult.WrongCredential
        } catch (_: Exception) {
            return UnlockResult.Error("无法读取助记词恢复密钥，数据可能已损坏")
        }

        val dek = secretKeyFromBytes(dekBytes)
        rewrapMaster(dek, newMasterPassword)
        prefs.resetFailures()
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
        val dek = secretKeyFromBytes(dekBytes)
        rewrapMaster(dek, newPassword)
        prefs.resetFailures()
        return UnlockResult.Success
    }

    /** Verifies the current master credential without unlocking or exposing the DEK to callers. */
    suspend fun verifyMasterCredential(masterPassword: String): UnlockResult {
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
            dekBytes.fill(0)
            prefs.resetFailures()
            UnlockResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (_: AEADBadTagException) {
            prefs.recordFailure()
            UnlockResult.WrongCredential
        } catch (_: Exception) {
            UnlockResult.Error("无法读取加密密钥，数据可能已损坏")
        }
    }

    /** Replace the security question / answer. Requires the vault to be currently unlocked. */
    suspend fun changeRecovery(question: String, answer: String) {
        require(question.isNotBlank()) { "安全问题不能为空" }
        require(answer.isNotBlank()) { "安全问题答案不能为空" }
        val dek = SessionManager.requireKey()
        val salt = CryptoManager.newSalt()
        val iterations = CryptoManager.DEFAULT_PBKDF2_ITERATIONS
        val recoveryKey = CryptoManager.deriveKey(normalizeAnswer(answer).toCharArray(), salt, iterations)
        val wrapped = wrapDek(recoveryKey, dek)
        prefs.updateRecoveryMaterial(
            recoverySalt = CryptoManager.encode(salt),
            recoveryWrappedDek = CryptoManager.encode(wrapped),
            recoveryQuestion = question,
            recoveryIterations = iterations
        )
    }

    suspend fun updateMnemonicRecovery(masterPassword: String, mnemonicPhrase: String): UnlockResult {
        val delay = unlockDelaySeconds()
        if (delay > 0) return UnlockResult.Delayed(delay)

        val dek = verifyMasterAndGetDek(masterPassword) ?: return UnlockResult.WrongCredential.also {
            prefs.recordFailure()
        }
        val normalizedMnemonic = mnemonicManager.normalize(mnemonicPhrase)
        if (!mnemonicManager.isValidPhrase(normalizedMnemonic)) return UnlockResult.Error("助记词格式无效")

        val salt = CryptoManager.newSalt()
        val mnemonicKey = mnemonicManager.deriveRecoveryKey(normalizedMnemonic, salt)
        val wrapped = wrapDek(mnemonicKey, dek)
        prefs.updateMnemonicMaterial(
            mnemonicSalt = CryptoManager.encode(salt),
            mnemonicWrappedDek = CryptoManager.encode(wrapped),
            mnemonicVerifierHash = mnemonicManager.verifierHash(normalizedMnemonic, salt)
        )
        prefs.resetFailures()
        return UnlockResult.Success
    }

    suspend fun disableMnemonicRecovery(masterPassword: String): UnlockResult {
        val delay = unlockDelaySeconds()
        if (delay > 0) return UnlockResult.Delayed(delay)

        if (verifyMasterAndGetDek(masterPassword) == null) {
            prefs.recordFailure()
            return UnlockResult.WrongCredential
        }
        prefs.clearMnemonicMaterial()
        prefs.resetFailures()
        return UnlockResult.Success
    }

    private suspend fun rewrapMaster(dek: SecretKey, newPassword: String) {
        val salt = CryptoManager.newSalt()
        val iterations = CryptoManager.DEFAULT_PBKDF2_ITERATIONS
        val masterKey = CryptoManager.deriveKey(newPassword.toCharArray(), salt, iterations)
        val wrapped = wrapDek(masterKey, dek)
        prefs.updateMasterMaterial(
            masterSalt = CryptoManager.encode(salt),
            masterWrappedDek = CryptoManager.encode(wrapped),
            masterIterations = iterations
        )
    }

    private suspend fun verifyMasterAndGetDek(masterPassword: String): SecretKey? {
        val saltB64 = prefs.masterSalt() ?: return null
        val wrappedB64 = prefs.masterWrappedDek() ?: return null
        return try {
            val masterKey = CryptoManager.deriveKey(
                masterPassword.toCharArray(),
                CryptoManager.decode(saltB64),
                prefs.masterIterations()
            )
            secretKeyFromBytes(CryptoManager.decrypt(masterKey, CryptoManager.decode(wrappedB64)))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // ---- Biometric ---------------------------------------------------------

    /**
     * Persists the DEK wrapped by the biometric-bound Keystore key.
     * [authenticatedCipher] must already have passed through BiometricPrompt in ENCRYPT mode.
     */
    suspend fun storeBiometricWrappedDek(authenticatedCipher: Cipher) {
        val dek = SessionManager.requireKey()
        val dekBytes = dek.encoded
        val ciphertext = try {
            authenticatedCipher.doFinal(dekBytes)
        } finally {
            dekBytes.fill(0)
        }
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
            SessionManager.unlock(secretKeyFromBytes(dekBytes))
            UnlockResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UnlockResult.Error("指纹解锁失败")
        }
    }

    fun lock() = SessionManager.lock()

    private fun wrapDek(wrappingKey: SecretKey, dek: SecretKey): ByteArray {
        val dekBytes = dek.encoded
        return try {
            CryptoManager.encrypt(wrappingKey, dekBytes)
        } finally {
            dekBytes.fill(0)
        }
    }

    private fun secretKeyFromBytes(bytes: ByteArray): SecretKey =
        try {
            SecretKeySpec(bytes, "AES")
        } finally {
            bytes.fill(0)
        }

    /** Normalises recovery answers so capitalisation / surrounding spaces don't matter. */
    private fun normalizeAnswer(answer: String): String = answer.trim().lowercase()
}
