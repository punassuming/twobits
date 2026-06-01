package com.shelfsnap.app

import com.shelfsnap.app.util.ApiKeyValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyValidatorTest {

    @Test
    fun `valid key with sk prefix and sufficient length is accepted`() {
        assertTrue(ApiKeyValidator.isValid("sk-abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun `valid project-scoped key is accepted`() {
        assertTrue(ApiKeyValidator.isValid("sk-proj-abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertTrue(ApiKeyValidator.isValid("  sk-abcdefghijklmnopqrstuvwxyz  "))
    }

    @Test
    fun `blank key is rejected`() {
        assertFalse(ApiKeyValidator.isValid(""))
        assertFalse(ApiKeyValidator.isValid("   "))
    }

    @Test
    fun `key without sk prefix is rejected`() {
        assertFalse(ApiKeyValidator.isValid("abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun `too-short key is rejected`() {
        assertFalse(ApiKeyValidator.isValid("sk-short"))
    }
}
