package com.twobits.pricedrop.ui.ask

import android.content.Context
import com.twobits.debuglog.DebugLogEntry
import com.twobits.debuglog.DebugLogEntryType
import com.twobits.debuglog.DebugLogStore
import com.twobits.localai.LiteRtLmEngine
import com.twobits.localai.LocalInferenceMemoryGuard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * NOT verified against sustained/idle use on a real device: whether [LiteRtLmEngine]/its
 * underlying `Engine` are safe to hold open across a longer-lived, possibly-idle Ask session
 * (rather than the short-lived construct-use-close pattern every other caller in this codebase
 * follows) hasn't been confirmed with real-device testing. Two defensive safeguards make that
 * safe by construction rather than waiting on that confirmation: [send] tears down and rebuilds
 * the engine if it's sat idle past [IDLE_TIMEOUT_MS] (covers the case where a caller never calls
 * [close] — e.g. the app is backgrounded mid-conversation), and a failure on a *reused* engine
 * (as opposed to a freshly-acquired one) triggers one automatic rebuild-and-retry before the
 * failure is surfaced to the caller. [send] still ultimately surfaces any failure as a normal
 * thrown exception, same as before.
 */
@Singleton
class LocalAskSession
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val debugLogStore: DebugLogStore,
    ) {
        private var engine: LiteRtLmEngine? = null
        private var engineModelFile: File? = null
        private var lastUsedAtMs: Long = 0L

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
            debugLogStore.record(
                DebugLogEntry(
                    timestampMs = startedAtMs,
                    type = DebugLogEntryType.AI_CALL,
                    op = "ask-start",
                    startMarker = true,
                    endpoint = "on-device",
                    model = modelFile.name,
                    requestSummary =
                        "prompt ${prompt.length} chars · ${LocalInferenceMemoryGuard.snapshot(context)?.summary() ?: "mem=unknown"}",
                    success = true,
                ),
            )
            return try {
                val wasReused = ensureEngine(modelFile, systemPrompt, startedAtMs)
                val response =
                    try {
                        requireNotNull(engine).generate(prompt)
                    } catch (e: Throwable) {
                        // Only a *reused* engine gets a silent rebuild-and-retry: a failure on a
                        // freshly-acquired engine is a real failure (bad model, no memory, etc.)
                        // and should surface immediately, not mask itself behind a second attempt
                        // that will just fail the same way.
                        if (!wasReused) throw e
                        close()
                        ensureEngine(modelFile, systemPrompt, startedAtMs)
                        requireNotNull(engine).generate(prompt)
                    }
                lastUsedAtMs = System.currentTimeMillis()
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        type = DebugLogEntryType.AI_CALL,
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
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        type = DebugLogEntryType.AI_CALL,
                        op = "ask",
                        endpoint = "on-device",
                        model = modelFile.name,
                        requestSummary = "prompt ${prompt.length} chars",
                        success = false,
                        responseSnippet = "${e.javaClass.simpleName}: ${e.message}",
                        durationMs = System.currentTimeMillis() - startedAtMs,
                        stackTrace = e.stackTraceToString(),
                    ),
                )
                throw e
            }
        }

        /**
         * Ensures [engine] is a live engine for [modelFile], reusing the current one when
         * possible. Returns whether an existing engine was reused (`false` means a fresh one was
         * just constructed). Idle engines past [IDLE_TIMEOUT_MS] are torn down and rebuilt rather
         * than reused, since staying open that long is exactly the untested-on-device scenario
         * this class's doc comment flags.
         */
        private suspend fun ensureEngine(
            modelFile: File,
            systemPrompt: String,
            startedAtMs: Long,
        ): Boolean {
            val idleTooLong = engine != null && System.currentTimeMillis() - lastUsedAtMs > IDLE_TIMEOUT_MS
            val canReuse = engine != null && engineModelFile == modelFile && !idleTooLong
            if (canReuse) return true

            close()
            // Engine construction synchronously opens and prepares the native model. On
            // a cold start that takes seconds, so never do it from AskViewModel's main
            // dispatcher or Android will treat the app as unresponsive. acquire() also
            // refuses up front when the device can't fit the model, and holds the
            // process-wide one-engine gate until close() — which is fine here, since
            // Ask is PriceDrop's only on-device inference.
            engine =
                withContext(Dispatchers.Default) {
                    LiteRtLmEngine.acquire(context, modelFile, systemInstruction = systemPrompt)
                }
            engineModelFile = modelFile
            // Only reached when a fresh engine was actually just constructed above (a
            // reused engine from an earlier turn in the same conversation has no load
            // step to report) — splits a future native crash's window into "died during
            // load" vs. "died during generation", same as every other local-inference
            // call site in the app.
            debugLogStore.record(
                DebugLogEntry(
                    timestampMs = System.currentTimeMillis(),
                    type = DebugLogEntryType.AI_CALL,
                    op = "ask-engine-loaded",
                    startMarker = true,
                    endpoint = "on-device",
                    model = modelFile.name,
                    requestSummary = LocalInferenceMemoryGuard.snapshot(context)?.summary() ?: "mem=unknown",
                    success = true,
                    durationMs = System.currentTimeMillis() - startedAtMs,
                ),
            )
            return false
        }

        /** Ends the current conversation's engine. Call on new conversation / screen exit. */
        fun close() {
            engine?.close()
            engine = null
            engineModelFile = null
        }

        private companion object {
            /** How long the engine may sit idle before [send] tears it down and rebuilds it. */
            const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L
        }
    }
