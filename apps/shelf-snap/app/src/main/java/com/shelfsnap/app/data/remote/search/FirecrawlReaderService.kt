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
 * on JS-heavy listings than Jina's lighter default engine, at the cost of being slower and more
 * expensive per read.
 *
 * Every call pins `location.country` to "US" (live-tested against a real eBay item page):
 * without it, eBay served a GDPR cookie-consent shell (~380 chars, no listing content) instead
 * of the actual page — a geo-triggered consent wall, not a bot-check, so the fix is a location
 * hint rather than a rendering-engine override. Applied unconditionally rather than scoped to
 * eBay the way Jina's bot-check workaround is: both apps are US-market shopping tools, so a US
 * location is a safe default for every read, not a marketplace-specific quirk to branch on.
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
                            add("location", JsonObject().apply { addProperty("country", "US") })
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
         * `{"success": true, "data": {"markdown": "...", "metadata": {...}}}` — confirmed against
         * both official SDKs' own response parsing (js-sdk's `res.data.data`, python-sdk's
         * `body["data"]`) and Firecrawl's docs. The top-level fallback guards only a future API
         * change, not present ambiguity.
         */
        private fun parseMarkdown(json: String): String? =
            runCatching {
                val root = JsonParser.parseString(json).asJsonObject
                (root.getAsJsonObject("data")?.get("markdown") ?: root.get("markdown"))?.asString
            }.getOrElse {
                Log.w(TAG, "Failed to parse Firecrawl response: ${it.javaClass.simpleName}")
                null
            }

        private companion object {
            const val TAG = "FirecrawlReaderService"
        }
    }
