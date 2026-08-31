package com.shelfsnap.app.data.remote.search

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PageReaderService] backed by Firecrawl (firecrawl.dev) — an alternative to Jina AI's Reader.
 * Firecrawl renders every page through a real headless browser by default, which may fare better
 * on JS-heavy or anti-bot listings than Jina's lighter default engine, at the cost of being
 * slower and more expensive per read.
 *
 * @see <a href="https://docs.firecrawl.dev/features/scrape">Firecrawl scrape API</a>
 */
@Singleton
class FirecrawlReaderService
    @Inject
    constructor() : PageReaderService {
        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                // Firecrawl always renders via a headless browser, so give it more room than
                // Jina's default-engine budget before giving up on the whole call.
                .callTimeout(40, TimeUnit.SECONDS)
                .build()

        override suspend fun read(
            pageUrl: String,
            apiKey: String,
            maxChars: Int,
        ): String? =
            withContext(Dispatchers.IO) {
                if (apiKey.isBlank() || pageUrl.isBlank()) return@withContext null

                val body =
                    JsonObject()
                        .apply {
                            addProperty("url", pageUrl)
                            add("formats", JsonArray().apply { add("markdown") })
                        }.toString()
                        .toRequestBody("application/json".toMediaType())

                val request =
                    Request
                        .Builder()
                        .url("https://api.firecrawl.dev/v2/scrape")
                        .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()

                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Firecrawl scrape failed for $pageUrl: HTTP ${response.code}")
                            return@use null
                        }
                        parseMarkdown(response.body?.string().orEmpty())
                            ?.trim()
                            ?.take(maxChars)
                            ?.ifBlank { null }
                    }
                }.getOrElse {
                    Log.w(TAG, "Firecrawl scrape threw for $pageUrl: ${it.javaClass.simpleName}")
                    null
                }
            }

        /**
         * The scraped markdown's exact JSON location isn't consistently documented across
         * Firecrawl's own examples (a top-level `markdown` field per its public repo's README,
         * a nested `data.markdown` in older docs) — check both rather than assuming one.
         */
        private fun parseMarkdown(json: String): String? =
            runCatching {
                val root = JsonParser.parseString(json).asJsonObject
                val nested = root.getAsJsonObject("data") ?: root.getAsJsonObject("document")
                (nested?.get("markdown") ?: root.get("markdown"))?.asString
            }.getOrElse {
                Log.w(TAG, "Failed to parse Firecrawl response: ${it.javaClass.simpleName}")
                null
            }

        private companion object {
            const val TAG = "FirecrawlReaderService"
        }
    }
