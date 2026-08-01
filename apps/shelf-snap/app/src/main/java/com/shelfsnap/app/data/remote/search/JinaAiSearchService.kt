package com.shelfsnap.app.data.remote.search

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [WebSearchService] backed by Jina AI Search (s.jina.ai).
 * JSON responses require a bearer token — free keys are available at jina.ai.
 */
@Singleton
class JinaAiSearchService
    @Inject
    constructor() : WebSearchService {
        override val provider = SearchProvider.JINA

        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                // readTimeout only caps the gap *between* reads, not the call as a whole — a
                // response that keeps trickling bytes just under that gap can run well past 20s.
                // Observed: a single broadening query here stretched the whole search phase to
                // 72s. callTimeout is a hard ceiling on the entire call regardless of how data
                // arrives, so one slow/stuck query can no longer dominate the phase this way.
                .callTimeout(20, TimeUnit.SECONDS)
                .build()

        override suspend fun search(
            query: String,
            apiKey: String,
            limit: Int,
        ): List<WebSearchResult> =
            withContext(Dispatchers.IO) {
                if (apiKey.isBlank()) {
                    throw IOException("Jina AI requires an API key for search results — add one in Settings")
                }
                val url =
                    "https://s.jina.ai/"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("q", query)
                        .build()

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .addHeader("Accept", "application/json")
                        .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                        .addHeader("X-Return-Format", "text")
                        .get()
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Jina AI search failed: HTTP ${response.code}")
                        throw IOException("Jina AI search failed: HTTP ${response.code}")
                    }
                    parse(response.body?.string().orEmpty(), limit)
                }
            }

        private fun parse(
            json: String,
            limit: Int,
        ): List<WebSearchResult> =
            runCatching {
                val data =
                    JsonParser
                        .parseString(json)
                        .asJsonObject
                        .getAsJsonArray("data") ?: return emptyList()
                data.take(limit).mapNotNull { el ->
                    val obj = el.asJsonObject
                    val title = obj.get("title")?.asString ?: return@mapNotNull null
                    val resultUrl = obj.get("url")?.asString ?: return@mapNotNull null
                    val snippet =
                        obj.get("description")?.asString
                            ?: obj.get("content")?.asString?.take(200)
                            ?: ""
                    WebSearchResult(title = title, url = resultUrl, snippet = snippet)
                }
            }.getOrElse {
                Log.w(TAG, "Failed to parse Jina AI response: ${it.javaClass.simpleName}")
                emptyList()
            }

        companion object {
            private const val TAG = "JinaAiSearch"
        }
    }
