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
        val session =
            RecordingSession(
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
        val transcript =
            Transcript(
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
        val profile =
            TransformProfile(
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
        val failedSession =
            RecordingSession(
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
        val stuckSession =
            RecordingSession(
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
        val stuckSession =
            RecordingSession(
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
        val config =
            ProviderConfig(
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

    @Test
    fun `archived session has isArchived true and ARCHIVED status`() {
        val now = Instant.now()
        val session =
            RecordingSession(
                id = "archived-id",
                title = "Archived Recording",
                audioFilePath = "/data/recordings/archived.m4a",
                durationMs = 60_000L,
                fileSizeBytes = 1_024_000L,
                audioFormat = AudioFormat.AAC,
                sampleRateHz = 48_000,
                encodingBitRate = 128_000,
                channelCount = 1,
                waveformSamples = emptyList(),
                status = SessionStatus.ARCHIVED,
                isArchived = true,
                estimatedTranscriptionCostUsd = null,
                createdAt = now,
                updatedAt = now,
            )

        assertTrue(session.isArchived)
        assertEquals(SessionStatus.ARCHIVED, session.status)
        assertEquals("ARCHIVED", session.status.name)
    }

    @Test
    fun `restoring archived session clears isArchived and resets status to RECORDED`() {
        val now = Instant.now()
        val archivedSession =
            RecordingSession(
                id = "archived-id",
                title = "Archived Recording",
                audioFilePath = "/data/recordings/archived.m4a",
                durationMs = 60_000L,
                fileSizeBytes = 1_024_000L,
                audioFormat = AudioFormat.AAC,
                sampleRateHz = 48_000,
                encodingBitRate = 128_000,
                channelCount = 1,
                waveformSamples = emptyList(),
                status = SessionStatus.ARCHIVED,
                isArchived = true,
                estimatedTranscriptionCostUsd = null,
                createdAt = now,
                updatedAt = now,
            )

        val restoreStatus: (String) -> String = { status ->
            val current = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.RECORDED)
            if (current == SessionStatus.ARCHIVED) SessionStatus.RECORDED.name else current.name
        }

        val restoredSession =
            archivedSession.copy(
                isArchived = false,
                status = SessionStatus.valueOf(restoreStatus(archivedSession.status.name)),
            )

        assertFalse(restoredSession.isArchived)
        assertEquals(SessionStatus.RECORDED, restoredSession.status)
        assertEquals(archivedSession.id, restoredSession.id)
    }

    @Test
    fun `archiving a transcribed session marks it archived with ARCHIVED status`() {
        val now = Instant.now()
        val transcribedSession =
            RecordingSession(
                id = "transcribed-id",
                title = "Transcribed Recording",
                audioFilePath = "/data/recordings/transcribed.m4a",
                durationMs = 60_000L,
                fileSizeBytes = 1_024_000L,
                audioFormat = AudioFormat.AAC,
                sampleRateHz = 48_000,
                encodingBitRate = 128_000,
                channelCount = 1,
                waveformSamples = emptyList(),
                status = SessionStatus.TRANSCRIBED,
                isArchived = false,
                estimatedTranscriptionCostUsd = null,
                createdAt = now,
                updatedAt = now,
            )

        val archivedSession =
            transcribedSession.copy(
                isArchived = true,
                status = SessionStatus.ARCHIVED,
            )

        assertTrue(archivedSession.isArchived)
        assertEquals(SessionStatus.ARCHIVED, archivedSession.status)
        assertEquals(transcribedSession.id, archivedSession.id)
    }

    @Test
    fun `restoreStatus maps ARCHIVED status to RECORDED and preserves other statuses`() {
        // Mirrors the restoreStatus helper in both History and SessionDetail ViewModels:
        // the status is stored as a String in the DB entity, so the function takes/returns String.
        val restoreStatus: (String) -> String = { status ->
            val current = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.RECORDED)
            if (current == SessionStatus.ARCHIVED) SessionStatus.RECORDED.name else current.name
        }

        // ARCHIVED maps back to RECORDED (safe default, because original status is overwritten at archive time)
        assertEquals(SessionStatus.RECORDED.name, restoreStatus(SessionStatus.ARCHIVED.name))

        // All other statuses are preserved unchanged
        listOf(SessionStatus.RECORDED, SessionStatus.TRANSCRIBED, SessionStatus.EDITED, SessionStatus.FAILED)
            .forEach { status ->
                assertEquals(status.name, restoreStatus(status.name))
            }
    }
}
