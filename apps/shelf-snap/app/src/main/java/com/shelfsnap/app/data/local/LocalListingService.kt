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
    ) {
        suspend fun refine(
            item: Item,
            platform: Platform,
            current: ListingCopy,
            modelFile: File,
        ): ListingCopy =
            runCatching {
                val systemPrompt = buildListingSystemPrompt(platform)
                val userMessage = buildListingUserMessage(item, current, platform)
                LiteRtLmEngine(context, modelFile, systemInstruction = systemPrompt).use { engine ->
                    parseListingJson(engine.generate(userMessage), current, platform.titleCharLimit)
                }
            }.getOrElse {
                Log.w(TAG, "Local listing refinement failed: ${it.javaClass.simpleName} — keeping current copy")
                current
            }

        companion object {
            private const val TAG = "LocalListingService"
        }
    }
