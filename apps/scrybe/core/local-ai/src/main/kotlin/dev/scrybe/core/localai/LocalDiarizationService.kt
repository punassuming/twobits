package dev.scrybe.core.localai

import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.SpeakerSegment
import dev.scrybe.core.transcription.DiarizationService
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDiarizationService
    @Inject
    constructor(
        private val localLlmService: LocalLlmService,
    ) : DiarizationService {
        override suspend fun diarize(
            sessionId: String,
            audioFile: File,
            transcriptText: String,
            providerType: ProviderType,
        ): Result<List<SpeakerSegment>> =
            runCatching {
                if (transcriptText.isBlank()) return@runCatching emptyList()

                // A failed local LLM call (most commonly: no on-device model installed) must
                // surface as an error, not collapse into an empty list — an empty result reads
                // as "no distinct speakers were detected" in the UI, hiding the real problem.
                val speakerTurns =
                    localLlmService
                        .identifySpeakerTurns(transcriptText)
                        .getOrElse { cause ->
                            error(
                                "On-device speaker identification failed (${cause.message ?: cause.javaClass.simpleName}). " +
                                    "Download the local model in AI configuration, or switch AI features off Local.",
                            )
                        }

                if (speakerTurns.size <= 1) return@runCatching emptyList()

                val durationMs = (audioFile.length() / BYTES_PER_MS_ESTIMATE).coerceAtLeast(1L)
                val segmentDurationMs = durationMs / speakerTurns.size

                speakerTurns.mapIndexed { index, speakerId ->
                    SpeakerSegment(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        speakerId = speakerId,
                        speakerLabel = null,
                        personId = null,
                        startMs = index * segmentDurationMs,
                        endMs = ((index + 1) * segmentDurationMs).coerceAtMost(durationMs),
                    )
                }
            }

        private companion object {
            // Rough estimate: AAC at 128 kbps ≈ 16 bytes/ms
            const val BYTES_PER_MS_ESTIMATE = 16L
        }
    }
