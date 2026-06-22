package com.offlinevault.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CryptoManagerTest {
    @Test fun acceptsSupportedPbkdf2IterationCounts() {
        assertEquals(
            CryptoManager.DEFAULT_PBKDF2_ITERATIONS,
            CryptoManager.requireValidIterations(CryptoManager.DEFAULT_PBKDF2_ITERATIONS)
        )
        assertEquals(
            CryptoManager.LEGACY_PBKDF2_ITERATIONS,
            CryptoManager.requireValidIterations(CryptoManager.LEGACY_PBKDF2_ITERATIONS)
        )
    }

    @Test fun rejectsUnboundedPbkdf2IterationCounts() {
        assertThrows(IllegalArgumentException::class.java) {
            CryptoManager.requireValidIterations(Int.MAX_VALUE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CryptoManager.requireValidIterations(1)
        }
    }
}
