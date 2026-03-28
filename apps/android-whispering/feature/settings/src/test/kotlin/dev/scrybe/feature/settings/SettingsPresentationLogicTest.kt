package dev.scrybe.feature.settings

import dev.scrybe.core.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPresentationLogicTest {
    @Test
    fun `OpenAI provider shows OpenAI api key config`() {
        assertTrue(shouldShowOpenAiApiKey(ProviderType.OPENAI.name))
    }

    @Test
    fun `non OpenAI provider hides OpenAI api key config`() {
        assertFalse(shouldShowOpenAiApiKey(ProviderType.LOCAL.name))
    }

    @Test
    fun `options summary lists visible alternatives and excludes selected value`() {
        val summary =
            buildOptionsSummary(
                selected = 48_000,
                options = listOf(16_000, 22_050, 44_100, 48_000),
                label = { "${it / 1000} kHz" },
            )

        assertEquals("Other options: 16 kHz • 22 kHz • 44 kHz", summary)
    }

    @Test
    fun `options summary is omitted when there are no alternatives`() {
        val summary =
            buildOptionsSummary(
                selected = "AAC",
                options = listOf("AAC"),
                label = { it },
            )

        assertNull(summary)
    }
}
