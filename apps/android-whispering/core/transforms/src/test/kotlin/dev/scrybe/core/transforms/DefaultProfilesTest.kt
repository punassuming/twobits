package dev.scrybe.core.transforms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProfilesTest {

    @Test
    fun `ALL contains exactly three profiles`() {
        assertEquals(3, DefaultProfiles.ALL.size)
    }

    @Test
    fun `CLEANUP_DICTATION is the default profile`() {
        assertTrue(DefaultProfiles.CLEANUP_DICTATION.isDefault)
    }

    @Test
    fun `SUMMARIZE is not the default profile`() {
        assertFalse(DefaultProfiles.SUMMARIZE.isDefault)
    }

    @Test
    fun `ACTION_ITEMS is not the default profile`() {
        assertFalse(DefaultProfiles.ACTION_ITEMS.isDefault)
    }

    @Test
    fun `each profile has a non-empty system prompt`() {
        DefaultProfiles.ALL.forEach { profile ->
            assertTrue(
                "Profile ${profile.name} has empty system prompt",
                profile.systemPrompt.isNotBlank()
            )
        }
    }

    @Test
    fun `each profile has a unique id`() {
        val ids = DefaultProfiles.ALL.map { it.id }.toSet()
        assertEquals(DefaultProfiles.ALL.size, ids.size)
    }
}
