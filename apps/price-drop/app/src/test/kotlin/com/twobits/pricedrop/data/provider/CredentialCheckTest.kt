package com.twobits.pricedrop.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialCheckTest {
    @Test
    fun `blank key is invalid for any provider`() {
        val result = CredentialCheck.check(PriceDropProvider.OPENAI, "   ")
        assertFalse(result.isValid)
        assertEquals("Enter an API key", result.message)
    }

    @Test
    fun `well-formed openai key is valid`() {
        val result = CredentialCheck.check(PriceDropProvider.OPENAI, "sk-abcdefghijklmnopqrstuvwx")
        assertTrue(result.isValid)
    }

    @Test
    fun `malformed openai key is rejected with guidance`() {
        val result = CredentialCheck.check(PriceDropProvider.OPENAI, "not-a-key")
        assertFalse(result.isValid)
        assertTrue(result.message.contains("sk-"))
    }

    @Test
    fun `non-openai provider accepts a reasonable key without the sk prefix`() {
        val result = CredentialCheck.check(PriceDropProvider.SHOPPING, "serpapi-1234567890")
        assertTrue(result.isValid)
    }

    @Test
    fun `non-openai provider rejects a too-short key`() {
        val result = CredentialCheck.check(PriceDropProvider.WEB_SEARCH, "abc")
        assertFalse(result.isValid)
    }

    @Test
    fun `openai key with surrounding whitespace is trimmed before checking`() {
        val result = CredentialCheck.check(PriceDropProvider.OPENAI, "  sk-abcdefghijklmnopqrstuvwx  ")
        assertTrue(result.isValid)
    }

    @Test
    fun `unknown mode value defaults to OFF`() {
        assertEquals(ProviderMode.OFF, ProviderMode.fromValue("bogus"))
        assertEquals(ProviderMode.OFF, ProviderMode.fromValue(null))
    }
}
