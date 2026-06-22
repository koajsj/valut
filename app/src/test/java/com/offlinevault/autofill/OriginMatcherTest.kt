package com.offlinevault.autofill

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginMatcherTest {
    @Test fun exactHostMatches() {
        assertTrue(OriginMatcher.matches("https://accounts.example.com/login", "accounts.example.com"))
    }

    @Test fun phishingSuffixDoesNotMatch() {
        assertFalse(OriginMatcher.matches("https://accounts.example.com", "accounts.example.com.evil.test"))
    }

    @Test fun parentAndSubdomainDoNotCrossMatch() {
        assertFalse(OriginMatcher.matches("https://example.com", "login.example.com"))
        assertFalse(OriginMatcher.matches("https://login.example.com", "example.com"))
    }

    @Test fun missingOriginNeverMatches() {
        assertFalse(OriginMatcher.matches("https://example.com", null))
    }
}
