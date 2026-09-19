package com.shelfsnap.app.data.local

import android.content.Context
import android.util.Log
import com.shelfsnap.app.data.listing.ListingCopy
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.remote.buildListingSystemPrompt
import com.shelfsnap.app.data.remote.buildListingUserMessage
import com.shelfsnap.app.data.remote.parseListingJson
import com.twobits.localai.LocalInferenceMemoryGuard
import com.twobits.localai.withLocalLlmEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LocalListingService.refine]'s result — [listing] is always present ([current] unchanged on
 * any failure, matching the cloud path's never-lose-data contract), [error] is only non-null on
 * failure, carrying a user-facing reason (e.g. an [com.twobits.localai.InsufficientMemoryException]'s
 * message) instead of leaving the caller with no way to tell "refined" from "silently failed".
 */
data class LocalListingResult(
    val listing: ListingCopy,
    val error: String? = null,
)

/**
 * On-device counterpart to [com.shelfsnap.app.data.remote.ListingGenerationService] — same
 * prompt/parsing logic (shared via [buildListingSystemPrompt]/[buildListingUserMessage]/
 * [parseListingJson]), routed through [withLocalLlmEngine] instead of OpenAI. Returns [current]
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
        ): LocalListingResult {
            val progressId = progressTracker.start("Refining listing…")
            return try {
                runCatching {
                    val systemPrompt = buildListingSystemPrompt(platform)
                    val userMessage = buildListingUserMessage(item, current, platform)
                    val startedAtMs = System.currentTimeMillis()
                    // Loading the local engine is a synchronous, blocking native model load —
                    // it doesn't hop dispatchers on its own, so a caller that launches this from a
                    // bare viewModelScope.launch {} (main-thread by default) would ANR. Every
                    // current caller happens to launch this off-main already, but that's an easy
                    // contract to break for a new one, so it's enforced here instead of trusted
                    // at every call site (same fix already applied to Scrybe's
                    // WhisperTranscriptionProvider).
                    withContext(Dispatchers.IO) {
                        progressTracker.update(progressId, "Loading local model…")
                        // Recorded — and awaited — immediately before the risky native call
                        // below, not after: a native crash or low-memory kill in LiteRT-LM ends
                        // the process with zero chance for any Kotlin try/catch to run, so this
                        // entry already being safely on disk is the only way to later see, from
                        // the Debug Log alone, that a listing refine was in flight (and how much
                        // memory was free) when it died. The "listing-refine-engine-loaded" entry
                        // below then splits that window into "died during load" vs. "died during
                        // generation" — same pattern as every other local-inference call site.
                        debugLogStore.record(
                            DebugLogEntry(
                                timestampMs = startedAtMs,
                                type = DebugLogEntryType.AI_CALL,
                                op = "listing-refine-start",
                                startMarker = true,
                                endpoint = "on-device",
                                model = modelFile.name,
                                requestSummary = LocalInferenceMemoryGuard.snapshot(context)?.summary() ?: "mem=unknown",
                                success = true,
                            ),
                        )
                        withLocalLlmEngine(context, modelFile, systemInstruction = systemPrompt) { engine ->
                            debugLogStore.record(
                                DebugLogEntry(
                                    timestampMs = System.currentTimeMillis(),
                                    type = DebugLogEntryType.AI_CALL,
                                    op = "listing-refine-engine-loaded",
                                    startMarker = true,
                                    endpoint = "on-device",
                                    model = modelFile.name,
                                    requestSummary = LocalInferenceMemoryGuard.snapshot(context)?.summary() ?: "mem=unknown",
                                    success = true,
                                    durationMs = System.currentTimeMillis() - startedAtMs,
                                ),
                            )
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
                            LocalListingResult(listing = parseListingJson(response, current, platform.titleCharLimit))
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
                    LocalListingResult(
                        listing = current,
                        error = localAiFailureMessage(it, genericMessage = "On-device listing refinement failed. Try Pro or BYOK instead."),
                    )
                }
            } finally {
                progressTracker.finish(progressId)
            }
        }

        companion object {
            private const val TAG = "LocalListingService"
        }
    }
