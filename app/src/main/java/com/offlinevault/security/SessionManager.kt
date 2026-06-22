package com.offlinevault.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.crypto.SecretKey

/**
 * Holds the in-memory unlocked state of the app. The Data Encryption Key (DEK) only ever lives
 * here while the vault is unlocked, and is dropped the moment the app is locked or backgrounded.
 */
object SessionManager {

    @Volatile
    private var dek: SecretKey? = null

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked

    val isUnlocked: Boolean get() = dek != null

    fun unlock(key: SecretKey) {
        dek = key
        _unlocked.value = true
    }

    fun lock() {
        dek = null
        _unlocked.value = false
    }

    /** Returns the active DEK or throws if the app is locked. */
    fun requireKey(): SecretKey =
        dek ?: throw IllegalStateException("Vault is locked")
}
