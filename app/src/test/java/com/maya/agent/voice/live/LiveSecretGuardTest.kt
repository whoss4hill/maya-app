package com.maya.agent.voice.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSecretGuardTest {

    @Test
    fun `flags private keys api keys tokens and jwt`() {
        assertTrue(LiveSecretGuard.looksLikeSecret("-----BEGIN PRIVATE KEY-----\nMII..."))
        assertTrue(LiveSecretGuard.looksLikeSecret("key: AIza" + "a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5"))
        assertTrue(LiveSecretGuard.looksLikeSecret("ghp_" + "a".repeat(36)))
        assertTrue(LiveSecretGuard.looksLikeSecret("github_pat_" + "aA1".repeat(11)))
        assertTrue(LiveSecretGuard.looksLikeSecret("xoxb-1234567890-abcdefghij"))
        assertTrue(LiveSecretGuard.looksLikeSecret("AKIAIOSFODNN7EXAMPLE"))
        assertTrue(LiveSecretGuard.looksLikeSecret("123456789:" + "A".repeat(35)))
        assertTrue(LiveSecretGuard.looksLikeSecret("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abcDEF123"))
        assertTrue(LiveSecretGuard.looksLikeSecret("Authorization: Bearer " + "t".repeat(30)))
    }

    @Test
    fun `flags labeled secrets and card numbers`() {
        assertTrue(LiveSecretGuard.looksLikeSecret("mera password: kela1234"))
        assertTrue(LiveSecretGuard.looksLikeSecret("otp is 482916"))
        assertTrue(LiveSecretGuard.looksLikeSecret("cvv 123"))
        // Luhn-valid test card
        assertTrue(LiveSecretGuard.looksLikeSecret("card 4111 1111 1111 1111"))
        // Placeholders are NOT secrets (same exclusion as backend)
        assertFalse(LiveSecretGuard.looksLikeSecret("password is <PASSWORD>"))
        assertFalse(LiveSecretGuard.looksLikeSecret("api_key = \${API_KEY}"))
    }

    @Test
    fun `passes ordinary tool output`() {
        assertFalse(LiveSecretGuard.looksLikeSecret("Timer set kar diya 10 minute ke liye"))
        assertFalse(LiveSecretGuard.looksLikeSecret("Screen pe ye hai: Home Search Cart Total 499"))
        assertFalse(LiveSecretGuard.looksLikeSecret(""))
        assertFalse(LiveSecretGuard.looksLikeSecret(null))
        // Luhn-invalid number string is not flagged
        assertFalse(LiveSecretGuard.looksLikeSecret("order id 4111 1111 1111 1112"))
    }
}
