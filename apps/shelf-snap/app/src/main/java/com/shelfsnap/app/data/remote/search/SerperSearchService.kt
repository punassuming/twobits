package com.shelfsnap.app.data.remote.search

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [WebSearchService] backed by Serper.dev — a materially cheaper Google-results scraper than
 * SearchAPI.io ($0.30–1.00/1k vs. SearchAPI's ~$0.004/call at a much higher monthly minimum).
 * The query string is passed straight through to Google, so `site:` operators work the same way
 * they do for SearchAPI's `google` engine. Unlike SearchAPI, Serper has no dedicated eBay engine,
 * so eBay-targeted queries here are plain-Google quality, not structured sold-listing quality.
 *
 * @see <a href="https://serper.dev">Serper.dev</a>
 */
@Singleton
class SerperSearchService
    @Inject
    constructor() : WebSearchService {
        override val provider = SearchProvider.SERPER

        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

        override suspend fun search(
            query: String,
            apiKey: String,
            limit: Int,
        ): List<WebSearchResult> =
            withContext(Dispatchers.IO) {
                if (apiKey.isBlank()) {
                    throw IOException("Serper.dev requires an API key — add one in Settings")
                }

                val body =
                    JsonObject()
                        .apply {
                            addProperty("q", query)
                            addProperty("num", limit.coerceIn(1, 20))
                        }.toString()
                        .toRequestBody("application/json".toMediaType())

                val request =
                    Request
                        .Builder()
                        .url("https://google.serper.dev/search")
                        .addHeader("X-API-KEY", apiKey.trim())
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Serper search failed: HTTP ${response.code}")
                        throw IOException("Serper search failed: HTTP ${response.code}")
                    }
                    parse(response.body?.string().orEmpty(), limit)
                }
            }

        private fun parse(
            json: String,
            limit: Int,
        ): List<WebSearchResult> =
            runCatching {
                val results =
                    JsonParser.parseString(json).asJsonObject.getAsJsonArray("organic")
                        ?: return emptyList()
                results.take(limit).mapNotNull { el ->
                    val obj = el.asJsonObject
                    val title = obj.get("title")?.asString ?: return@mapNotNull null
                    val resultUrl = obj.get("link")?.asString ?: return@mapNotNull null
                    val snippet = obj.get("snippet")?.asString ?: ""
                    WebSearchResult(title = title, url = resultUrl, snippet = snippet)
                }
            }.getOrElse {
                Log.w(TAG, "Failed to parse Serper response: ${it.javaClass.simpleName}")
                emptyList()
            }

        private companion object {
            const val TAG = "SerperSearchService"
        }
    }
