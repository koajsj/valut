package com.offlinevault.security

object UnlockThrottle {
    private const val THRESHOLD = 3
    private const val BASE_DELAY_SECONDS = 5L
    private const val MAX_DELAY_SECONDS = 5 * 60L

    fun remainingSeconds(attempts: Int, lastFailedAt: Long, now: Long): Int {
        if (attempts < THRESHOLD) return 0
        val exponent = (attempts - THRESHOLD).coerceIn(0, 6)
        val delay = (BASE_DELAY_SECONDS * (1L shl exponent)).coerceAtMost(MAX_DELAY_SECONDS)
        val elapsed = ((now - lastFailedAt) / 1000).coerceAtLeast(0)
        return (delay - elapsed).coerceAtLeast(0).toInt()
    }
}
