package com.twobits.pricedrop.ui.ask

import android.content.Context
import com.twobits.localai.LiteRtLmEngine
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
    ) {
        private var engine: LiteRtLmEngine? = null
        private var engineModelFile: File? = null

        suspend fun send(
            prompt: String,
            modelFile: File,
            systemPrompt: String,
        ): String {
            if (engine == null || engineModelFile != modelFile) {
                close()
                engine = LiteRtLmEngine(context, modelFile, systemInstruction = systemPrompt)
                engineModelFile = modelFile
            }
            return requireNotNull(engine).generate(prompt)
        }

        /** Ends the current conversation's engine. Call on new conversation / screen exit. */
        fun close() {
            engine?.close()
            engine = null
            engineModelFile = null
        }
    }
