package com.offlinevault.security

/**
 * Lets in-app flows that legitimately background the activity (system file pickers, the biometric
 * prompt) suppress the "lock on background" behaviour for exactly one background/foreground cycle,
 * so the Data Encryption Key is not dropped mid-operation.
 */
object LockGuard {
    @Volatile
    var suppressNextBackground: Boolean = false
}
