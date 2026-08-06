package com.twobits.pricedrop.ui.ask

import android.content.Context
import com.twobits.localai.LiteRtLmEngine
import com.twobits.pricedrop.data.local.AiCallDebugEntry
import com.twobits.pricedrop.data.local.AiCallDebugStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session-scoped multi-turn local chat, owned by [AskViewModel]. [PriceDropApiClient.chat] is
 * stateless-per-request (it takes the full conversation history every call, matching a plain
 * HTTP call) — a local engine works the opposite way: [LiteRtLmEngine]'s `Conversation` already
 * accumulates turns across repeated [send] calls on the *same* engine instance, so re-sending
 * the whole growing history every turn (the naive alternative) would be redundant work with
 * compounding latency and eventual context-window overflow. This class exists to hold one
 * engine open across a conversation instead of constructing a fresh one per message.
 *
 * NOT verified against sustained/idle use: whether [LiteRtLmEngine]/its underlying `Engine`
 * are safe to hold open across a longer-lived, possibly-idle Ask session (rather than the
 * short-lived construct-use-close pattern every other caller in this codebase follows) hasn't
 * been tested on a real device. If it isn't, callers should still degrade gracefully since
 * [send] surfaces failures as a normal thrown exception.
 */
@Singleton
class LocalAskSession
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val aiCallDebugStore: AiCallDebugStore,
    ) {
        private var engine: LiteRtLmEngine? = null
        private var engineModelFile: File? = null

        suspend fun send(
            prompt: String,
            modelFile: File,
            systemPrompt: String,
        ): String {
            val startedAtMs = System.currentTimeMillis()
            // Recorded — and awaited — before the risky native call below, not after: a native
            // crash in LiteRT-LM's engine construction/generate kills the process with zero
            // chance for any Kotlin try/catch to run, so this "start" entry being safely on disk
            // beforehand is the only way to see, after the fact, that this call was in flight.
            aiCallDebugStore.record(
                AiCallDebugEntry(
                    timestampMs = startedAtMs,
                    op = "ask-start",
                    endpoint = "on-device",
                    model = modelFile.name,
                    requestSummary = "prompt ${prompt.length} chars",
                    success = true,
                ),
            )
            return try {
                if (engine == null || engineModelFile != modelFile) {
                    close()
                    engine = LiteRtLmEngine(context, modelFile, systemInstruction = systemPrompt)
                    engineModelFile = modelFile
                }
                val response = requireNotNull(engine).generate(prompt)
                aiCallDebugStore.record(
                    AiCallDebugEntry(
                        timestampMs = System.currentTimeMillis(),
                        op = "ask",
                        endpoint = "on-device",
                        model = modelFile.name,
                        requestSummary = "prompt ${prompt.length} chars",
                        success = true,
                        responseSnippet = "${response.length} chars",
                        durationMs = System.currentTimeMillis() - startedAtMs,
                    ),
                )
                response
            } catch (e: Throwable) {
                aiCallDebugStore.record(
                    AiCallDebugEntry(
                        timestampMs = System.currentTimeMillis(),
                        op = "ask",
                        endpoint = "on-device",
                        model = modelFile.name,
                        requestSummary = "prompt ${prompt.length} chars",
                        success = false,
                        responseSnippet = "${e.javaClass.simpleName}: ${e.message}",
                        durationMs = System.currentTimeMillis() - startedAtMs,
                    ),
                )
                throw e
            }
        }

        /** Ends the current conversation's engine. Call on new conversation / screen exit. */
        fun close() {
            engine?.close()
            engine = null
            engineModelFile = null
        }
    }
