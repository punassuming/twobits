package com.shelfsnap.app.data.local

import android.content.Context
import android.util.Log
import com.shelfsnap.app.data.listing.ListingCopy
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.remote.buildListingSystemPrompt
import com.shelfsnap.app.data.remote.buildListingUserMessage
import com.shelfsnap.app.data.remote.parseListingJson
import com.twobits.localai.LiteRtLmEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device counterpart to [com.shelfsnap.app.data.remote.ListingGenerationService] — same
 * prompt/parsing logic (shared via [buildListingSystemPrompt]/[buildListingUserMessage]/
 * [parseListingJson]), routed through [LiteRtLmEngine] instead of OpenAI. Returns [current]
 * unchanged on any failure, matching the cloud path's never-lose-data contract.
 */
@Singleton
class LocalListingService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val debugLogStore: DebugLogStore,
        private val progressTracker: LocalAnalysisProgressTracker,
    ) {
        suspend fun refine(
            item: Item,
            platform: Platform,
            current: ListingCopy,
            modelFile: File,
        ): ListingCopy {
            val progressId = progressTracker.start("Refining listing…")
            return try {
                runCatching {
                    val systemPrompt = buildListingSystemPrompt(platform)
                    val userMessage = buildListingUserMessage(item, current, platform)
                    val startedAtMs = System.currentTimeMillis()
                    // Constructing LiteRtLmEngine is a synchronous, blocking native model load —
                    // it doesn't hop dispatchers on its own, so a caller that launches this from a
                    // bare viewModelScope.launch {} (main-thread by default) would ANR. Every
                    // current caller happens to launch this off-main already, but that's an easy
                    // contract to break for a new one, so it's enforced here instead of trusted
                    // at every call site (same fix already applied to Scrybe's
                    // WhisperTranscriptionProvider).
                    withContext(Dispatchers.IO) {
                        progressTracker.update(progressId, "Loading local model…")
                        LiteRtLmEngine(context, modelFile, systemInstruction = systemPrompt).use { engine ->
                            progressTracker.update(progressId, "Generating listing locally…")
                            val response =
                                engine.generate(userMessage) { progress ->
                                    val elapsedSeconds = progress.elapsedMs / 1_000
                                    val detail =
                                        if (progress.receivedMessageCount == 0) {
                                            "Waiting for local model… ${elapsedSeconds}s"
                                        } else {
                                            "Generating listing locally… ${elapsedSeconds}s"
                                        }
                                    progressTracker.update(progressId, detail)
                                }
                            debugLogStore.record(
                                DebugLogEntry(
                                    timestampMs = System.currentTimeMillis(),
                                    type = DebugLogEntryType.AI_CALL,
                                    op = "listing-refine",
                                    endpoint = "on-device",
                                    model = modelFile.name,
                                    requestSummary = "platform=${platform.name}",
                                    success = true,
                                    responseSnippet = "${response.length} chars",
                                    durationMs = System.currentTimeMillis() - startedAtMs,
                                ),
                            )
                            parseListingJson(response, current, platform.titleCharLimit)
                        }
                    }
                }.getOrElse {
                    Log.w(TAG, "Local listing refinement failed: ${it.javaClass.simpleName} — keeping current copy")
                    // One AI_CALL entry, not a second separate CRASH entry for the same failure —
                    // the unified log would otherwise show every failure here twice. stackTrace
                    // (a field shared across every entry type) carries the same diagnostic detail
                    // a standalone crash entry would have.
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.AI_CALL,
                            op = "listing-refine",
                            endpoint = "on-device",
                            model = modelFile.name,
                            requestSummary = "platform=${platform.name}",
                            success = false,
                            responseSnippet = "${it.javaClass.simpleName}: ${it.message}",
                            stackTrace = it.stackTraceToString(),
                        ),
                    )
                    current
                }
            } finally {
                progressTracker.finish(progressId)
            }
        }

        companion object {
            private const val TAG = "LocalListingService"
        }
    }
