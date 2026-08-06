package com.shelfsnap.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.shelfsnap.app.data.remote.DraftItemResult
import com.shelfsnap.app.data.remote.VisionAnalysisService
import com.shelfsnap.app.data.remote.parseDraftItemJson
import com.twobits.localai.LiteRtBackend
import com.twobits.localai.LiteRtLmEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device counterpart to [VisionAnalysisService] — same JSON schema/parsing (shared via
 * [parseDraftItemJson]), routed through [LiteRtLmEngine.generateWithImage] instead of OpenAI.
 *
 * EXPERIMENTAL: relies on Gemma 4 E2B/E4B actually supporting image input via LiteRT-LM's
 * vision path, which is evidenced (litert-community lists these repos under "Multi-Modality
 * Models") but not independently verified end-to-end — see [LiteRtLmEngine]'s doc comment.
 * Only sends a single photo regardless of the user's multi-photo-analysis setting; multi-image
 * local vision is a separate, untested question.
 */
@Singleton
class LocalVisionService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val crashLogStore: CrashLogStore,
        private val progressTracker: LocalAnalysisProgressTracker,
    ) {
        suspend fun analyse(
            photoPath: String,
            modelFile: File,
        ): DraftItemResult {
            val progressId = progressTracker.start("Analyzing photo…")
            return try {
                runCatching {
                    // Constructing LiteRtLmEngine is a synchronous, blocking native model load —
                    // it doesn't hop dispatchers on its own, so a caller that launches this from a
                    // bare viewModelScope.launch {} (main-thread by default) would ANR. Every
                    // current caller happens to launch this off-main already, but that's an easy
                    // contract to break for a new one, so it's enforced here instead of trusted
                    // at every call site (same fix already applied to Scrybe's
                    // WhisperTranscriptionProvider).
                    //
                    // visionBackend must be set for generateWithImage() to work at all — LiteRT-LM's
                    // own docs only demonstrate sending Content.ImageFile with visionBackend
                    // configured, and leaving it null (the previous state here) while sending an
                    // image is undocumented, unsupported usage. That mismatch — not a Kotlin-level
                    // bug — is the leading suspect for this path's crash-with-nothing-in-the-log:
                    // native ML runtimes tend to hard-abort (SIGABRT) rather than throw a catchable
                    // exception for unsupported configurations, which no runCatching here can see.
                    withContext(Dispatchers.IO) {
                        val downscaledPath = downscaleForLocalInference(photoPath)
                        try {
                            LiteRtLmEngine(
                                context,
                                modelFile,
                                systemInstruction = VisionAnalysisService.SYSTEM_PROMPT,
                                visionBackend = LiteRtBackend.CPU,
                            ).use { engine ->
                                val response = engine.generateWithImage(File(downscaledPath), VisionAnalysisService.USER_PROMPT)
                                parseDraftItemJson(response)
                            }
                        } finally {
                            if (downscaledPath != photoPath) File(downscaledPath).delete()
                        }
                    }
                }.getOrElse {
                    Log.w(TAG, "Local vision analysis failed: ${it.javaClass.simpleName}")
                    crashLogStore.record(it)
                    DraftItemResult(error = "On-device vision analysis failed. Try Pro or BYOK instead.")
                }
            } finally {
                progressTracker.finish(progressId)
            }
        }

        /**
         * A full-resolution phone capture (commonly 4032x3024 or larger) decoded natively
         * alongside a multi-GB Gemma model already resident in the same process is a real
         * out-of-memory risk this engine has no guardrail against on its own — mirrors the same
         * downscale-before-send precaution [VisionAnalysisService] already applies for the cloud
         * path (see its `encodeImageToBase64`), just writing the result to a temp file since
         * [LiteRtLmEngine.generateWithImage] takes a file path, not bytes. [MAX_DIM] is smaller
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
            const val MAX_DIM = 1024
            const val JPEG_QUALITY = 90
        }
    }
