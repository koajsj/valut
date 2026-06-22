package com.offlinevault.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MnemonicManagerTest {

    private val manager = MnemonicManager()

    @Test
    fun normalize_collapsesWhitespaceAndLowercases() {
        val normalized = manager.normalize("  ABLE   about\nABSENT access  acid across adapt admit adult advice aerobic affair ")
        assertEquals(
            "able about absent access acid across adapt admit adult advice aerobic affair",
            normalized
        )
    }

    @Test
    fun isValidPhrase_requiresExactlyTwelveKnownWords() {
        assertTrue(
            manager.isValidPhrase("able about absent access acid across adapt admit adult advice aerobic affair")
        )
        assertFalse(
            manager.isValidPhrase("able about absent access acid across adapt admit adult advice aerobic")
        )
        assertFalse(
            manager.isValidPhrase("able about absent access acid across adapt admit adult advice aerobic unknown")
        )
    }

    @Test
    fun verifierMatches_onlyForSamePhraseAndSalt() {
        val salt = CryptoManager.newSalt()
        val phrase = "able about absent access acid across adapt admit adult advice aerobic affair"
        val verifier = manager.verifierHash(phrase, salt)

        assertTrue(manager.verifierMatches(phrase, salt, verifier))
        assertFalse(
            manager.verifierMatches(
                "able about absent access acid across adapt admit adult advice aerobic agent",
                salt,
                verifier
            )
        )
    }
}
