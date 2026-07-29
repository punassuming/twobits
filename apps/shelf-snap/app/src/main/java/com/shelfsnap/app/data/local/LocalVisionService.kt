package com.shelfsnap.app.data.local

import android.content.Context
import android.util.Log
import com.shelfsnap.app.data.remote.DraftItemResult
import com.shelfsnap.app.data.remote.VisionAnalysisService
import com.shelfsnap.app.data.remote.parseDraftItemJson
import com.twobits.localai.LiteRtLmEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
    ) {
        suspend fun analyse(
            photoPath: String,
            modelFile: File,
        ): DraftItemResult =
            runCatching {
                LiteRtLmEngine(context, modelFile, systemInstruction = VisionAnalysisService.SYSTEM_PROMPT).use { engine ->
                    val response = engine.generateWithImage(File(photoPath), VisionAnalysisService.USER_PROMPT)
                    parseDraftItemJson(response)
                }
            }.getOrElse {
                Log.w(TAG, "Local vision analysis failed: ${it.javaClass.simpleName}")
                DraftItemResult(error = "On-device vision analysis failed. Try Pro or BYOK instead.")
            }

        companion object {
            private const val TAG = "LocalVisionService"
        }
    }
