package com.shelfsnap.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.shelfsnap.app.data.remote.DraftItemResult
import com.shelfsnap.app.data.remote.VisionAnalysisService
import com.shelfsnap.app.data.remote.parseDraftItemJson
import com.twobits.debuglog.DebugLogEntry
import com.twobits.debuglog.DebugLogEntryType
import com.twobits.debuglog.DebugLogStore
import com.twobits.localai.LiteRtBackend
import com.twobits.localai.LiteRtLmEngine
import com.twobits.localai.LocalInferenceMemoryGuard
import com.twobits.localai.withLocalLlmEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device counterpart to [VisionAnalysisService] — same JSON schema/parsing (shared via
 * [parseDraftItemJson]), routed through [com.twobits.localai.LiteRtLmEngine.generateWithImage] instead of OpenAI.
 *
 * EXPERIMENTAL: relies on Gemma 4 E2B/E4B actually supporting image input via LiteRT-LM's
 * vision path, which is evidenced (litert-community lists these repos under "Multi-Modality
 * Models") but not independently verified end-to-end — see [com.twobits.localai.LiteRtLmEngine]'s doc comment.
 *
 * [analyse] sends exactly one photo. Multiple photos are handled by [analyseMultiple], not by
 * sending more than one image in a single [com.twobits.localai.LiteRtLmEngine.generateWithImage]
 * call — this codebase has never attempted that, and it's unverified whether the underlying
 * vendor API even supports it. Instead each photo is analysed individually on the same engine
 * instance, then one more text-only turn on that same [com.twobits.localai.LiteRtLmEngine]
 * conversation asks the model to combine the per-photo results — cheaper than reloading the
 * model per photo, and the combination turn gets every prior image + response as context for
 * free since [com.twobits.localai.LiteRtLmEngine]'s underlying `Conversation` accumulates turns
 * (the same behavior PriceDrop's `LocalAskSession` already relies on for its multi-turn chat).
 */
@Singleton
class LocalVisionService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val debugLogStore: DebugLogStore,
        private val progressTracker: LocalAnalysisProgressTracker,
    ) {
        suspend fun analyse(
            photoPath: String,
            modelFile: File,
        ): DraftItemResult {
            val progressId = progressTracker.start("Analyzing photo…")
            return try {
                runCatching {
                    // Loading the engine is a synchronous, blocking native model load — it
                    // doesn't hop dispatchers on its own, so a caller that launches this from a
                    // bare viewModelScope.launch {} (main-thread by default) would ANR. Every
                    // current caller happens to launch this off-main already, but that's an easy
                    // contract to break for a new one, so it's enforced here instead of trusted
                    // at every call site (same fix already applied to Scrybe's
                    // WhisperTranscriptionProvider).
                    //
                    // visionBackend must be set for generateWithImage() to work at all — LiteRT-LM's
                    // own docs only demonstrate sending Content.ImageFile with visionBackend
                    // configured; leaving it null while sending an image is unsupported usage that
                    // a native runtime answers with an abort, not an exception.
                    withContext(Dispatchers.IO) {
                        val downscaledPath = downscaleForLocalInference(photoPath)
                        val startedAtMs = System.currentTimeMillis()
                        // Recorded — and awaited — immediately before the risky native call below,
                        // not after: a native crash or low-memory kill in LiteRT-LM's vision path
                        // ends the process with zero chance for any Kotlin try/catch to run, so
                        // this entry already being safely on disk is the only way to later see,
                        // from the AI call log alone, which call (model, photo size, and how much
                        // memory the device had) was in flight when it died — there is no matching
                        // "vision-analyze" entry after it if so. The "vision-engine-loaded" entry
                        // below then splits that window in two: a death before it is the model
                        // load, a death after it is the image prompt itself.
                        val memorySummary = LocalInferenceMemoryGuard.snapshot(context)?.summary() ?: "mem=unknown"
                        debugLogStore.record(
                            DebugLogEntry(
                                timestampMs = startedAtMs,
                                type = DebugLogEntryType.AI_CALL,
                                op = "vision-analyze-start",
                                startMarker = true,
                                endpoint = "on-device",
                                model = modelFile.name,
                                requestSummary =
                                    "photo=${File(downscaledPath).name} (${File(downscaledPath).length()} bytes) · $memorySummary",
                                success = true,
                            ),
                        )
                        try {
                            progressTracker.update(progressId, "Loading local model…")
                            withLocalLlmEngine(
                                context,
                                modelFile,
                                systemInstruction = VisionAnalysisService.SYSTEM_PROMPT,
                                visionBackend = LiteRtBackend.CPU,
                            ) { engine ->
                                debugLogStore.record(
                                    DebugLogEntry(
                                        timestampMs = System.currentTimeMillis(),
                                        type = DebugLogEntryType.AI_CALL,
                                        op = "vision-engine-loaded",
                                        startMarker = true,
                                        endpoint = "on-device",
                                        model = modelFile.name,
                                        requestSummary = LocalInferenceMemoryGuard.snapshot(context)?.summary() ?: "mem=unknown",
                                        success = true,
                                        durationMs = System.currentTimeMillis() - startedAtMs,
                                    ),
                                )
                                progressTracker.update(progressId, "Analyzing photo…")
                                val response =
                                    engine.generateWithImage(File(downscaledPath), VisionAnalysisService.USER_PROMPT) { progress ->
                                        // No elapsed time baked into this text anymore — the
                                        // footer now shows that itself, ticking from the
                                        // operation's own startedAtMs rather than this one field
                                        // this callback happens to expose.
                                        val detail =
                                            if (progress.receivedMessageCount == 0) {
                                                "Waiting for local model…"
                                            } else {
                                                "Analyzing photo…"
                                            }
                                        progressTracker.update(progressId, detail)
                                    }
                                debugLogStore.record(
                                    DebugLogEntry(
                                        timestampMs = System.currentTimeMillis(),
                                        type = DebugLogEntryType.AI_CALL,
                                        op = "vision-analyze",
                                        endpoint = "on-device",
                                        model = modelFile.name,
                                        requestSummary = "photo=${File(downscaledPath).name}",
                                        success = true,
                                        responseSnippet = "${response.length} chars",
                                        durationMs = System.currentTimeMillis() - startedAtMs,
                                    ),
                                )
                                parseDraftItemJson(response)
                            }
                        } finally {
                            if (downscaledPath != photoPath) File(downscaledPath).delete()
                        }
                    }
                }.getOrElse {
                    Log.w(TAG, "Local vision analysis failed: ${it.javaClass.simpleName}")
                    // One AI_CALL entry, not a second separate CRASH entry for the same failure —
                    // the unified log would otherwise show every failure here twice. stackTrace
                    // (a field shared across every entry type) carries the same diagnostic detail
                    // a standalone crash entry would have.
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.AI_CALL,
                            op = "vision-analyze",
                            endpoint = "on-device",
                            model = modelFile.name,
                            requestSummary = "photo=${File(photoPath).name}",
                            success = false,
                            responseSnippet = "${it.javaClass.simpleName}: ${it.message}",
                            stackTrace = it.stackTraceToString(),
                        ),
                    )
                    DraftItemResult(
                        error = localAiFailureMessage(it, genericMessage = "On-device vision analysis failed. Try Pro or BYOK instead."),
                    )
                }
            } finally {
                progressTracker.finish(progressId)
            }
        }

        /**
         * Analyses 2+ photos of the same item: one [analyse]-equivalent turn per photo on a
         * single shared engine instance, then one more text-only turn on that same
         * conversation asking the model to combine the per-photo JSON results into one. Falls
         * back to [analyse] when given 0-1 photos.
         */
        suspend fun analyseMultiple(
            photoPaths: List<String>,
            modelFile: File,
        ): DraftItemResult {
            if (photoPaths.size <= 1) {
                val singlePath = photoPaths.firstOrNull() ?: return DraftItemResult(error = "No photo to analyse.")
                return analyse(singlePath, modelFile)
            }
            val progressId = progressTracker.start("Analyzing photo 1 of ${photoPaths.size}…")
            return try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        withLocalLlmEngine(
                            context,
                            modelFile,
                            systemInstruction = VisionAnalysisService.SYSTEM_PROMPT,
                            visionBackend = LiteRtBackend.CPU,
                        ) { engine ->
                            val results =
                                photoPaths.mapIndexed { index, photoPath ->
                                    analysePhotoOnEngine(engine, photoPath, modelFile, index, photoPaths.size, progressId)
                                }
                            val successful = results.filter { it.error == null }
                            if (successful.isEmpty()) {
                                results.last()
                            } else {
                                progressTracker.update(progressId, "Combining results…")
                                combineResults(engine, modelFile, successful.size) ?: successful.first()
                            }
                        }
                    }
                }.getOrElse {
                    Log.w(TAG, "Local multi-photo vision analysis failed: ${it.javaClass.simpleName}")
                    DraftItemResult(
                        error = localAiFailureMessage(it, genericMessage = "On-device vision analysis failed. Try Pro or BYOK instead."),
                    )
                }
            } finally {
                progressTracker.finish(progressId)
            }
        }

        /** One photo's turn within an [analyseMultiple] batch, on an already-acquired [engine]. */
        private suspend fun analysePhotoOnEngine(
            engine: LiteRtLmEngine,
            photoPath: String,
            modelFile: File,
            index: Int,
            total: Int,
            progressId: String,
        ): DraftItemResult {
            val label = "photo ${index + 1}/$total"
            val downscaledPath = downscaleForLocalInference(photoPath)
            val startedAtMs = System.currentTimeMillis()
            return try {
                val memorySummary = LocalInferenceMemoryGuard.snapshot(context)?.summary() ?: "mem=unknown"
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = startedAtMs,
                        type = DebugLogEntryType.AI_CALL,
                        op = "vision-analyze-start",
                        startMarker = true,
                        endpoint = "on-device",
                        model = modelFile.name,
                        requestSummary =
                            "$label photo=${File(downscaledPath).name} (${File(downscaledPath).length()} bytes) · $memorySummary",
                        success = true,
                    ),
                )
                progressTracker.update(progressId, "Analyzing $label…")
                val response =
                    engine.generateWithImage(File(downscaledPath), VisionAnalysisService.USER_PROMPT) { progress ->
                        val detail = if (progress.receivedMessageCount == 0) "Waiting for local model…" else "Analyzing $label…"
                        progressTracker.update(progressId, detail)
                    }
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        type = DebugLogEntryType.AI_CALL,
                        op = "vision-analyze",
                        endpoint = "on-device",
                        model = modelFile.name,
                        requestSummary = "$label photo=${File(downscaledPath).name}",
                        success = true,
                        responseSnippet = "${response.length} chars",
                        durationMs = System.currentTimeMillis() - startedAtMs,
                    ),
                )
                parseDraftItemJson(response)
            } catch (e: Exception) {
                Log.w(TAG, "Local vision analysis failed for $label: ${e.javaClass.simpleName}")
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        type = DebugLogEntryType.AI_CALL,
                        op = "vision-analyze",
                        endpoint = "on-device",
                        model = modelFile.name,
                        requestSummary = "$label photo=${File(photoPath).name}",
                        success = false,
                        responseSnippet = "${e.javaClass.simpleName}: ${e.message}",
                        stackTrace = e.stackTraceToString(),
                    ),
                )
                DraftItemResult(
                    error = localAiFailureMessage(e, genericMessage = "On-device vision analysis failed. Try Pro or BYOK instead."),
                )
            } finally {
                if (downscaledPath != photoPath) File(downscaledPath).delete()
            }
        }

        /**
         * The synthesis turn at the end of an [analyseMultiple] batch — a plain text prompt on
         * the same [engine]/conversation, which already has every prior photo + per-photo JSON
         * response as context. Returns `null` (rather than throwing) if the combine turn itself
         * fails or its response doesn't parse, so the caller can fall back to a single photo's
         * result instead of failing the whole batch over a synthesis-only problem.
         */
        private suspend fun combineResults(
            engine: LiteRtLmEngine,
            modelFile: File,
            successfulCount: Int,
        ): DraftItemResult? {
            val startedAtMs = System.currentTimeMillis()
            return runCatching {
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = startedAtMs,
                        type = DebugLogEntryType.AI_CALL,
                        op = "vision-combine-start",
                        startMarker = true,
                        endpoint = "on-device",
                        model = modelFile.name,
                        requestSummary = "combining $successfulCount photo analyses",
                        success = true,
                    ),
                )
                val response = engine.generate(COMBINE_PROMPT)
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        type = DebugLogEntryType.AI_CALL,
                        op = "vision-combine",
                        endpoint = "on-device",
                        model = modelFile.name,
                        requestSummary = "combining $successfulCount photo analyses",
                        success = true,
                        responseSnippet = "${response.length} chars",
                        durationMs = System.currentTimeMillis() - startedAtMs,
                    ),
                )
                parseDraftItemJson(response)
            }.getOrElse { e ->
                Log.w(TAG, "Local vision combine step failed: ${e.javaClass.simpleName}")
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        type = DebugLogEntryType.AI_CALL,
                        op = "vision-combine",
                        endpoint = "on-device",
                        model = modelFile.name,
                        requestSummary = "combining $successfulCount photo analyses",
                        success = false,
                        responseSnippet = "${e.javaClass.simpleName}: ${e.message}",
                        stackTrace = e.stackTraceToString(),
                    ),
                )
                null
            }
        }

        /**
         * A full-resolution phone capture (commonly 4032x3024 or larger) decoded natively
         * alongside a multi-GB Gemma model already resident in the same process is a real
         * out-of-memory risk this engine has no guardrail against on its own — mirrors the same
         * downscale-before-send precaution [VisionAnalysisService] already applies for the cloud
         * path (see its `encodeImageToBase64`), just writing the result to a temp file since
         * [com.twobits.localai.LiteRtLmEngine.generateWithImage] takes a file path, not bytes. [MAX_DIM] is smaller
         * than the cloud path's 2048px cap since this competes with the model for the device's
         * own memory rather than a server's. Falls back to the original [photoPath] if decoding
         * fails for any reason, so a failure here doesn't block the analysis attempt outright.
         */
        private fun downscaleForLocalInference(photoPath: String): String =
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(photoPath, bounds)
                var inSampleSize = 1
                while (bounds.outWidth / inSampleSize > MAX_DIM || bounds.outHeight / inSampleSize > MAX_DIM) {
                    inSampleSize *= 2
                }
                if (inSampleSize == 1) return photoPath

                val scaled = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
                val bitmap = BitmapFactory.decodeFile(photoPath, scaled) ?: return photoPath
                val tempFile = File.createTempFile("local_vision_", ".jpg", context.cacheDir)
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                bitmap.recycle()
                tempFile.absolutePath
            }.getOrDefault(photoPath)

        private companion object {
            const val TAG = "LocalVisionService"
            const val COMBINE_PROMPT =
                "You've now analysed each photo of this item individually above. Combine your " +
                    "findings into one final JSON object using the exact same schema, resolving " +
                    "any differences by preferring whichever answer is more specific or " +
                    "confident. Return ONLY the JSON."
            const val MAX_DIM = 1024
            const val JPEG_QUALITY = 90
        }
    }
