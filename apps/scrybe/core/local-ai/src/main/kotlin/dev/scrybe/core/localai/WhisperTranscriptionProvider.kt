package dev.scrybe.core.localai

import com.twobits.debuglog.DebugLogEntry
import com.twobits.debuglog.DebugLogEntryType
import com.twobits.debuglog.DebugLogStore
import com.twobits.localai.LocalInferenceGate
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transcription.TranscriptResult
import dev.scrybe.core.transcription.TranscriptionChunkProgressTracker
import dev.scrybe.core.transcription.TranscriptionOptions
import dev.scrybe.core.transcription.TranscriptionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperTranscriptionProvider
    @Inject
    constructor(
        private val modelManager: LocalModelManager,
        private val debugLogStore: DebugLogStore,
        private val preferencesDataStore: AppPreferencesDataStore,
        private val chunkProgressTracker: TranscriptionChunkProgressTracker,
    ) : TranscriptionProvider {
        override val providerType: ProviderType = ProviderType.LOCAL

        override suspend fun transcribe(
            audioFile: File,
            options: TranscriptionOptions,
        ): Result<TranscriptResult> {
            // An on-device run makes no network calls, so without this the AI call log shows
            // nothing at all for a local transcription regardless of model or outcome —
            // indistinguishable from transcription never running (the exact bug already fixed
            // once for diarization/insights via DiarizationServiceFacade/InsightServiceFacade,
            // but missed here since this provider has no equivalent local/cloud facade to carry
            // the fix — it's the local branch of TranscriptionOrchestrator's provider map).
            //
            // Start and completion entries are both written unconditionally, and must stay that
            // way: the start entry is what a crashed run leaves behind as its only evidence, and
            // an unmatched one raises a crash warning to the user. Gating only the completion
            // behind this toggle made every successful transcription look like a crash. Only the
            // per-chunk timing detail below is optional.
            val debugEnabled = preferencesDataStore.debugDiarization.first()

            suspend fun record(
                success: Boolean,
                snippet: String,
                durationMs: Long? = null,
                stackTrace: String? = null,
            ) {
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        type = DebugLogEntryType.AI_CALL,
                        op = "transcribe",
                        endpoint = "on-device",
                        model = modelManager.selectedWhisperModel.value.filePrefix,
                        requestSummary = "file=${audioFile.name}",
                        success = success,
                        responseSnippet = snippet,
                        durationMs = durationMs,
                        stackTrace = stackTrace,
                    ),
                )
            }

            // Measures decode + model-load + chunked-transcribe together, not just the model
            // call — this is what actually answers "why does local transcription feel slow",
            // since decode/model-construction can dominate for a short clip. Declared outside
            // runCatching, not just inside the withContext block below, so the onFailure branch
            // below can report a real duration too instead of leaving every failed/cancelled
            // attempt's debug log entry with no duration at all.
            val startedAtMs = System.currentTimeMillis()

            return runCatching {
                val (modelDir, model) =
                    modelManager.activeWhisperDir()
                        ?: run {
                            record(
                                success = false,
                                snippet = "Whisper model not downloaded",
                                durationMs = System.currentTimeMillis() - startedAtMs,
                            )
                            // A bare `return` here exits the whole function directly, bypassing
                            // the `.also { }` below that clears this on every other exit path.
                            chunkProgressTracker.clear()
                            return Result.failure(IllegalStateException("Whisper model not downloaded"))
                        }

                // Decoding the recording (MediaExtractor/MediaCodec) and constructing
                // WhisperEngine (a synchronous native model load off disk) are both blocking —
                // neither hops dispatchers on its own, so a caller that launches this from a
                // bare viewModelScope.launch {} (main-thread by default) would ANR. Every
                // current caller happens to be right (auto-transcribe uses an IO-scoped
                // coroutine), but that's an easy contract to break for a new one, so it's
                // enforced here instead of trusted at every call site.
                withContext(Dispatchers.IO) {
                    val decoded = AudioDecoder.decode(audioFile)
                    // Recorded — and awaited — immediately before the risky native call below, not
                    // after: a native crash in WhisperEngine's ONNX model load/decode kills the
                    // process with zero chance for any Kotlin try/catch to run, so this entry
                    // already being safely on disk is the only way to later see, from the debug
                    // log alone, that a transcription was in flight when it crashed — there's no
                    // matching "transcribe" entry after it if so (see DebugLogStore.staleStartWarning).
                    // Written unconditionally, not gated on debugEnabled like the detail below —
                    // a native crash gives no chance to flip a setting first.
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = startedAtMs,
                            type = DebugLogEntryType.AI_CALL,
                            op = "transcribe-start",
                            startMarker = true,
                            endpoint = "on-device",
                            model = model.filePrefix,
                            requestSummary = "file=${audioFile.name}",
                            success = true,
                        ),
                    )
                    // Whisper is a native model like any other, and until now it was the one
                    // engine that ignored the process-wide gate — so a transcription could hold a
                    // Whisper model resident while a diarization or insight pass loaded a
                    // multi-gigabyte LiteRT-LM model alongside it, which is exactly the combined
                    // footprint the gate exists to prevent. Safe to wait here: the LiteRT follow-up
                    // work takes the finished transcript as input, so it never runs inside this
                    // block and the two can only ever queue, never deadlock.
                    LocalInferenceGate.withGate {
                        WhisperEngine(modelDir, model.filePrefix).use { engine ->
                            // Per-window timings are the one measurement that separates "the
                            // model is slow on this device" from "a window is stuck" — a run that
                            // hangs leaves no trace otherwise, and a run that finishes says
                            // nothing about how the time was spent. The one piece of detail still
                            // gated on the toggle, since it costs work on every decode window.
                            val chunkTimingsMs = mutableListOf<Long>()
                            val text =
                                engine.transcribe(decoded.samples, decoded.sampleRateHz) { index, total, elapsedMs ->
                                    // Unconditional, unlike the timing collection below: progress
                                    // is a status signal, not a debugging aid, so it isn't gated
                                    // behind the same toggle.
                                    chunkProgressTracker.update(completed = index + 1, total = total)
                                    if (debugEnabled) chunkTimingsMs += elapsedMs
                                }
                            record(
                                success = true,
                                snippet = "${text.length} chars${chunkTimingSummary(chunkTimingsMs)}",
                                durationMs = System.currentTimeMillis() - startedAtMs,
                            )
                            TranscriptResult(
                                text = text,
                                language = "en",
                                durationSeconds = null,
                            )
                        }
                    }
                }
            }.onFailure { error ->
                record(
                    success = false,
                    snippet = "${error.javaClass.simpleName}: ${error.message}",
                    durationMs = System.currentTimeMillis() - startedAtMs,
                    stackTrace = error.stackTraceToString(),
                )
            }.also {
                // Runs on every exit from the runCatching above — success, a caught failure, or a
                // cancellation — so a stale "chunk 4 of 7" can never survive into the next
                // transcription's own "Transcribing…" before its first chunk lands.
                chunkProgressTracker.clear()
            }
        }

        /** e.g. ` · 11 chunks · avg 4.2s/chunk · max 6.1s`; empty when nothing was collected. */
        private fun chunkTimingSummary(chunkTimingsMs: List<Long>): String {
            if (chunkTimingsMs.isEmpty()) return ""
            val avgSeconds = chunkTimingsMs.average() / MS_PER_SECOND
            val maxSeconds = chunkTimingsMs.max() / MS_PER_SECOND
            return " · ${chunkTimingsMs.size} chunks · avg ${"%.1f".format(avgSeconds)}s/chunk · max ${"%.1f".format(maxSeconds)}s"
        }

        private companion object {
            const val MS_PER_SECOND = 1_000.0
        }
    }
