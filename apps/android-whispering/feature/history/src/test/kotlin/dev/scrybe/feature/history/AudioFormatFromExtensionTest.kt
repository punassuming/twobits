package dev.scrybe.feature.history

import dev.scrybe.core.model.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFormatFromExtensionTest {
    @Test
    fun `m4a maps to AAC`() {
        assertEquals(AudioFormat.AAC, audioFormatFromExtension("m4a"))
    }

    @Test
    fun `mp4 maps to MP4`() {
        assertEquals(AudioFormat.MP4, audioFormatFromExtension("mp4"))
    }

    @Test
    fun `ogg maps to OGG`() {
        assertEquals(AudioFormat.OGG, audioFormatFromExtension("ogg"))
    }

    @Test
    fun `webm maps to WEBM`() {
        assertEquals(AudioFormat.WEBM, audioFormatFromExtension("webm"))
    }

    @Test
    fun `unknown extension defaults to AAC`() {
        assertEquals(AudioFormat.AAC, audioFormatFromExtension("wav"))
    }

    @Test
    fun `uppercase extension maps correctly`() {
        assertEquals(AudioFormat.AAC, audioFormatFromExtension("M4A"))
        assertEquals(AudioFormat.OGG, audioFormatFromExtension("OGG"))
    }
}
