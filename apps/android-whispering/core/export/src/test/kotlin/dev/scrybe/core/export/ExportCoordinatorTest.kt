package dev.scrybe.core.export

import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.Transcript
import dev.scrybe.core.model.TranscriptType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class ExportCoordinatorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val markdownExporter = MarkdownExporter()
    private val textExporter = TextExporter()
    private val jsonExporter = JsonExporter()
    private val coordinator = ExportCoordinator(markdownExporter, textExporter, jsonExporter)

    private val testSession =
        RecordingSession(
            id = "session-123",
            title = "My Recording",
            audioFilePath = "/data/test.m4a",
            durationMs = 30_000L,
            fileSizeBytes = 512_000L,
            audioFormat = AudioFormat.AAC,
            sampleRateHz = 48_000,
            encodingBitRate = 128_000,
            channelCount = 1,
            waveformSamples = listOf(0.1f, 0.3f, 0.2f),
            status = SessionStatus.TRANSCRIBED,
            isArchived = false,
            estimatedTranscriptionCostUsd = 0.01,
            createdAt = Instant.ofEpochMilli(0),
            updatedAt = Instant.ofEpochMilli(0),
        )

    private val testTranscripts =
        listOf(
            Transcript(
                id = "t1",
                sessionId = "session-123",
                content = "This is the raw transcript.",
                type = TranscriptType.RAW,
                sourceTranscriptId = null,
                providerType = ProviderType.OPENAI,
                transformProfileId = null,
                transformRunId = null,
                createdAt = Instant.ofEpochMilli(0),
            ),
        )

    @Test
    fun `markdown export creates file with correct extension`() {
        val result =
            coordinator.export(
                session = testSession,
                transcripts = testTranscripts,
                format = ExportFormat.MARKDOWN,
                outputDir = tempFolder.root,
            )

        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertTrue(file.name.endsWith(".md"))
        assertTrue(file.exists())
        assertTrue(file.readText().contains("My Recording"))
        assertTrue(file.readText().contains("This is the raw transcript."))
    }

    @Test
    fun `text export creates file with correct extension`() {
        val result =
            coordinator.export(
                session = testSession,
                transcripts = testTranscripts,
                format = ExportFormat.TXT,
                outputDir = tempFolder.root,
            )

        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertTrue(file.name.endsWith(".txt"))
        assertTrue(file.exists())
        assertTrue(file.readText().contains("My Recording"))
    }

    @Test
    fun `json export creates file with correct extension`() {
        val result =
            coordinator.export(
                session = testSession,
                transcripts = testTranscripts,
                format = ExportFormat.JSON,
                outputDir = tempFolder.root,
            )

        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertTrue(file.name.endsWith(".json"))
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("session-123"))
        assertTrue(content.contains("My Recording"))
    }

    @Test
    fun `markdown export includes transformed transcripts`() {
        val transformedTranscripts =
            testTranscripts +
                Transcript(
                    id = "t2",
                    sessionId = "session-123",
                    content = "Cleaned up text.",
                    type = TranscriptType.TRANSFORMED,
                    sourceTranscriptId = "t1",
                    providerType = ProviderType.OPENAI,
                    transformProfileId = "profile-1",
                    transformRunId = "run-1",
                    createdAt = Instant.ofEpochMilli(1000),
                )

        val result = markdownExporter.export(testSession, transformedTranscripts, tempFolder.newFolder())
        assertTrue(result.isSuccess)
        val content = result.getOrThrow().readText()
        assertTrue(content.contains("Raw Transcript"))
        assertTrue(content.contains("Transformed Outputs"))
        assertTrue(content.contains("Cleaned up text."))
    }
}
