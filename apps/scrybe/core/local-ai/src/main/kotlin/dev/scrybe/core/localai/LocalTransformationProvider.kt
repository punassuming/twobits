package dev.scrybe.core.localai

import android.content.Context
import com.twobits.localai.LocalInferenceMemoryGuard
import com.twobits.localai.withLocalLlmEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transcription.DebugLogEntry
import dev.scrybe.core.transcription.DebugLogEntryType
import dev.scrybe.core.transcription.DebugLogStore
import dev.scrybe.core.transforms.TransformInput
import dev.scrybe.core.transforms.TransformResult
import dev.scrybe.core.transforms.TransformationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalTransformationProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val modelManager: LocalModelManager,
        private val preferencesDataStore: AppPreferencesDataStore,
        private val debugLogStore: DebugLogStore,
    ) : TransformationProvider {
        override val providerType: ProviderType = ProviderType.LOCAL

        override suspend fun transform(input: TransformInput): Result<TransformResult> =
            runCatching {
                val selectedModel = preferencesDataStore.localLlmModel.first()
                val modelFile =
                    modelManager.llmModelFile(selectedModel)
                        ?: modelManager.anyLlmReady()?.let { modelManager.llmModelFile(it) }
                        ?: error("No local model downloaded. Go to Settings → Provider → Local to download one.")

                val transcript = input.combinedTranscriptText ?: input.transcriptText
                val prompt = "Transcript:\n$transcript\n\nOutput only the result."
                val startedAtMs = System.currentTimeMillis()
                // Recorded — and awaited — immediately before the risky native call below, not
                // after: a native crash or low-memory kill in LiteRT-LM kills the process with
                // zero chance for any Kotlin try/catch to run, so this entry already being safely
                // on disk is the only way to later see, from the Debug Log alone, that a
                // transform was in flight (and how much memory was free) when it died. The
                // "transform-engine-loaded" entry below then splits that window into "died during
                // load" vs. "died during generation" — same pattern as every other local-inference
                // call site in the app.
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = startedAtMs,
                        type = DebugLogEntryType.AI_CALL,
                        op = "transform-start",
                        startMarker = true,
                        endpoint = "on-device",
                        model = modelFile.name,
                        requestSummary = LocalInferenceMemoryGuard.snapshot(context)?.summary() ?: "mem=unknown",
                        success = true,
                    ),
                )
                try {
                    withContext(Dispatchers.Default) {
                        withLocalLlmEngine(context, modelFile, systemInstruction = input.systemPrompt) { engine ->
                            debugLogStore.record(
                                DebugLogEntry(
                                    timestampMs = System.currentTimeMillis(),
                                    type = DebugLogEntryType.AI_CALL,
                                    op = "transform-engine-loaded",
                                    endpoint = "on-device",
                                    model = modelFile.name,
                                    requestSummary = LocalInferenceMemoryGuard.snapshot(context)?.summary() ?: "mem=unknown",
                                    success = true,
                                    durationMs = System.currentTimeMillis() - startedAtMs,
                                ),
                            )
                            val response = engine.generate(prompt)
                            debugLogStore.record(
                                DebugLogEntry(
                                    timestampMs = System.currentTimeMillis(),
                                    type = DebugLogEntryType.AI_CALL,
                                    op = "transform",
                                    endpoint = "on-device",
                                    model = modelFile.name,
                                    success = true,
                                    responseSnippet = "${response.length} chars",
                                    durationMs = System.currentTimeMillis() - startedAtMs,
                                ),
                            )
                            TransformResult(
                                transformedText = response.trim(),
                                modelName = selectedModel.displayName,
                            )
                        }
                    }
                } catch (e: Throwable) {
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.AI_CALL,
                            op = "transform",
                            endpoint = "on-device",
                            model = modelFile.name,
                            success = false,
                            responseSnippet = "${e.javaClass.simpleName}: ${e.message}",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                            stackTrace = e.stackTraceToString(),
                        ),
                    )
                    throw e
                }
            }
    }
