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

    private const val MAX_PENDING_AGE_MS = 5 * 60 * 1000L

    private data class PendingCapture(
        val capture: Capture,
        val createdAt: Long = System.currentTimeMillis()
    )

    @Volatile
    private var pending: PendingCapture? = null

    fun set(capture: Capture) {
        pending = PendingCapture(capture)
    }

    /** Returns the pending capture (if any) and clears it. */
    fun consume(): Capture? {
        val current = pending
        pending = null
        if (current == null) return null
        val expired = System.currentTimeMillis() - current.createdAt > MAX_PENDING_AGE_MS
        return if (expired) null else current.capture
    }
}
