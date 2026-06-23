package com.offlinevault.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore

/**
 * Wraps an AndroidKeyStore AES key that is bound to biometric authentication.
 *
 * The flow:
 *  - When the user enables biometric unlock we generate a Keystore key that requires user auth.
 *  - We encrypt the DEK with a [Cipher] obtained from [encryptCipher] (gated by BiometricPrompt).
 *  - The resulting IV + ciphertext is stored in DataStore.
 *  - On biometric unlock we obtain [decryptCipher] (also gated by BiometricPrompt) to recover the DEK.
 */
object KeystoreManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "offline_vault_biometric_key"
    private const val GCM_TAG_BITS = 128
    private const val IV_LENGTH = 12

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        val ks = keyStore()
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun deleteKey() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    fun hasKey(): Boolean = runCatching { keyStore().containsAlias(KEY_ALIAS) }.getOrDefault(false)

    /** Cipher used to ENCRYPT the DEK. Must be passed to BiometricPrompt before use. */
    fun encryptCipher(): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher
    }

    /** Cipher used to DECRYPT the DEK with the stored IV. Must be passed to BiometricPrompt. */
    fun decryptCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher
    }

    /** Splits stored blob into IV + ciphertext. */
    fun splitIv(blob: ByteArray): Pair<ByteArray, ByteArray> {
        require(blob.size >= IV_LENGTH + GCM_TAG_BITS / 8) { "Invalid biometric key blob" }
        val iv = blob.copyOfRange(0, IV_LENGTH)
        val ct = blob.copyOfRange(IV_LENGTH, blob.size)
        return iv to ct
    }

    fun joinIv(iv: ByteArray, ciphertext: ByteArray): ByteArray = iv + ciphertext
}
