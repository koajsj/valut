package com.offlinevault.autofill

/**
 * Holds one credential captured by the autofill Save flow while the vault is locked.
 *
 * The value lives only in process memory (never persisted in the clear). It is consumed and
 * encrypted into the vault the next time the user unlocks, then cleared. If the process dies
 * before the user unlocks, the pending save is simply dropped — the same safety trade-off as
 * the in-memory data-encryption key.
 */
object PendingAutofillSave {

    data class Capture(val identifier: String, val username: String, val password: String)

    @Volatile
    private var pending: Capture? = null

    fun set(capture: Capture) {
        pending = capture
    }

    /** Returns the pending capture (if any) and clears it. */
    fun consume(): Capture? {
        val current = pending
        pending = null
        return current
    }
}
