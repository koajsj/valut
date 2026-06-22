package com.offlinevault.security

/** Versioned field encryption for searchable metadata that must not remain plaintext at rest. */
object EncryptedField {
    private const val PREFIX = "enc:v1:"

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    fun encrypt(value: String): String {
        if (isEncrypted(value)) return value
        return PREFIX + CryptoManager.encryptString(SessionManager.requireKey(), value)
    }

    fun decrypt(value: String): String {
        if (!isEncrypted(value)) return value // Legacy v1 row, migrated after the first unlock.
        return CryptoManager.decryptString(SessionManager.requireKey(), value.removePrefix(PREFIX))
    }
}

class VaultDataCorruptionException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
