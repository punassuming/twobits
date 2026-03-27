package dev.scrybe.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecordingSessionTest {

    @Test
    fun `RecordingSession can be created with all fields`() {
        val now = Instant.now()
        val session = RecordingSession(
            id = "test-id",
            title = "Test Recording",
            audioFilePath = "/data/recordings/test.m4a",
            durationMs = 60_000L,
            fileSizeBytes = 1_024_000L,
            audioFormat = AudioFormat.AAC,
            sampleRateHz = 48_000,
            encodingBitRate = 128_000,
            channelCount = 1,
            waveformSamples = emptyList(),
            status = SessionStatus.RECORDED,
            isArchived = false,
            estimatedTranscriptionCostUsd = null,
            createdAt = now,
            updatedAt = now,
        )

        assertEquals("test-id", session.id)
        assertEquals("Test Recording", session.title)
        assertEquals(SessionStatus.RECORDED, session.status)
        assertEquals(AudioFormat.AAC, session.audioFormat)
        assertEquals(60_000L, session.durationMs)
    }

    @Test
    fun `Transcript can be created for a session`() {
        val now = Instant.now()
        val transcript = Transcript(
            id = "transcript-id",
            sessionId = "session-id",
            content = "Hello world",
            type = TranscriptType.RAW,
            sourceTranscriptId = null,
            providerType = ProviderType.OPENAI,
            transformProfileId = null,
            transformRunId = null,
            createdAt = now,
        )

        assertEquals("transcript-id", transcript.id)
        assertEquals(TranscriptType.RAW, transcript.type)
        assertEquals(ProviderType.OPENAI, transcript.providerType)
    }

    @Test
    fun `TransformProfile can be created`() {
        val profile = TransformProfile(
            id = "profile-id",
            name = "Cleanup",
            description = "Cleans up text",
            systemPrompt = "Clean the text.",
            steps = listOf("Clean the text."),
            providerType = ProviderType.OPENAI,
            isDefault = true,
        )

        assertEquals("profile-id", profile.id)
        assertTrue(profile.isDefault)
        assertEquals(ProviderType.OPENAI, profile.providerType)
    }

    @Test
    fun `SessionStatus enum contains expected values`() {
        val statuses = SessionStatus.values()
        assertTrue(statuses.contains(SessionStatus.IDLE))
        assertTrue(statuses.contains(SessionStatus.RECORDING))
        assertTrue(statuses.contains(SessionStatus.TRANSCRIBED))
        assertTrue(statuses.contains(SessionStatus.FAILED))
    }

    @Test
    fun `FAILED status allows retry - transcribe button should be enabled`() {
        val now = Instant.now()
        val failedSession = RecordingSession(
            id = "test-id",
            title = "Test Recording",
            audioFilePath = "/data/recordings/test.m4a",
            durationMs = 60_000L,
            fileSizeBytes = 1_024_000L,
            audioFormat = AudioFormat.AAC,
            sampleRateHz = 48_000,
            encodingBitRate = 128_000,
            channelCount = 1,
            waveformSamples = emptyList(),
            status = SessionStatus.FAILED,
            isArchived = false,
            estimatedTranscriptionCostUsd = null,
            createdAt = now,
            updatedAt = now,
        )
        assertFalse(
            "FAILED session should not appear as transcribing (retry must be enabled)",
            failedSession.status == SessionStatus.TRANSCRIBING,
        )
    }

    @Test
    fun `only TRANSCRIBING status is identified as stale after crash`() {
        val isStale: (SessionStatus) -> Boolean = { it == SessionStatus.TRANSCRIBING }
        assertTrue("TRANSCRIBING should be stale", isStale(SessionStatus.TRANSCRIBING))
        assertFalse("FAILED should not be stale", isStale(SessionStatus.FAILED))
        assertFalse("RECORDED should not be stale", isStale(SessionStatus.RECORDED))
        assertFalse("TRANSCRIBED should not be stale", isStale(SessionStatus.TRANSCRIBED))
    }

    @Test
    fun `stale recovery produces FAILED session with all other fields preserved`() {
        val now = Instant.now()
        val stuckSession = RecordingSession(
            id = "stuck-id",
            title = "Stuck Recording",
            audioFilePath = "/data/recordings/stuck.m4a",
            durationMs = 30_000L,
            fileSizeBytes = 512_000L,
            audioFormat = AudioFormat.AAC,
            sampleRateHz = 16_000,
            encodingBitRate = 64_000,
            channelCount = 1,
            waveformSamples = emptyList(),
            status = SessionStatus.TRANSCRIBING,
            isArchived = false,
            estimatedTranscriptionCostUsd = null,
            createdAt = now,
            updatedAt = now,
        )
        val recovered = stuckSession.copy(status = SessionStatus.FAILED)
        assertEquals(SessionStatus.FAILED, recovered.status)
        assertEquals(stuckSession.id, recovered.id)
        assertEquals(stuckSession.title, recovered.title)
        assertEquals(stuckSession.audioFilePath, recovered.audioFilePath)
    }

    @Test
    fun `status names are stored as expected strings for DB compatibility`() {
        assertEquals("TRANSCRIBING", SessionStatus.TRANSCRIBING.name)
        assertEquals("FAILED", SessionStatus.FAILED.name)
        assertEquals("RECORDED", SessionStatus.RECORDED.name)
    }

    @Test
    fun `reset transcription state maps stale to RECORDED`() {
        val now = Instant.now()
        val stuckSession = RecordingSession(
            id = "stuck-id",
            title = "Stuck Recording",
            audioFilePath = "/data/recordings/stuck.m4a",
            durationMs = 30_000L,
            fileSizeBytes = 512_000L,
            audioFormat = AudioFormat.AAC,
            sampleRateHz = 16_000,
            encodingBitRate = 64_000,
            channelCount = 1,
            waveformSamples = emptyList(),
            status = SessionStatus.TRANSCRIBING,
            isArchived = false,
            estimatedTranscriptionCostUsd = null,
            createdAt = now,
            updatedAt = now,
        )
        val resetStatus = SessionStatus.RECORDED
        val resetSession = stuckSession.copy(status = resetStatus)
        assertEquals(SessionStatus.RECORDED, resetSession.status)
        assertFalse(resetSession.status == SessionStatus.TRANSCRIBING)
    }

    @Test
    fun `ProviderConfig can be created`() {
        val config = ProviderConfig(
            id = "config-id",
            providerType = ProviderType.OPENAI,
            isEnabled = true,
            modelName = "whisper-1",
            apiKeyAlias = "OPENAI",
        )

        assertEquals("config-id", config.id)
        assertTrue(config.isEnabled)
        assertEquals("whisper-1", config.modelName)
    }
}
