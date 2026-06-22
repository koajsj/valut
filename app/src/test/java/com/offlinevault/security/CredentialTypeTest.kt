package com.offlinevault.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialTypeTest {
    @Test fun passwordLengthIsBounded() {
        assertTrue(CredentialType.PASSWORD.isValid("a".repeat(8)))
        assertFalse(CredentialType.PASSWORD.isValid("a".repeat(129)))
        assertEquals(128, CredentialType.PASSWORD.sanitize("a".repeat(200)).length)
    }
}
