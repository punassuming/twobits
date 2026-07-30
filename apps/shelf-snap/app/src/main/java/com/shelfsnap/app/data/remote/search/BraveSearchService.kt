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
 * [WebSearchService] backed by the Brave Search API.
 * Requires a subscription token, supplied as the search API key.
 *
 * @see <a href="https://api-dashboard.search.brave.com/app/documentation">Brave Search API</a>
 */
@Singleton
class BraveSearchService
    @Inject
    constructor() : WebSearchService {
        override val provider = SearchProvider.BRAVE

        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                // Hard ceiling on the whole call — see JinaAiSearchService for why readTimeout
                // alone isn't enough (it only caps the gap between reads, not total duration).
                .callTimeout(20, TimeUnit.SECONDS)
                .build()

        override suspend fun search(
            query: String,
            apiKey: String,
            limit: Int,
        ): List<WebSearchResult> =
            withContext(Dispatchers.IO) {
                if (apiKey.isBlank()) {
                    throw IOException("Brave Search requires an API key — add one in Settings")
                }

                val url =
                    "https://api.search.brave.com/res/v1/web/search"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("q", query)
                        .addQueryParameter("count", limit.coerceIn(1, 20).toString())
                        .build()

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .addHeader("Accept", "application/json")
                        .addHeader("X-Subscription-Token", apiKey)
                        .get()
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Brave search failed: HTTP ${response.code}")
                        throw IOException("Brave search failed: HTTP ${response.code}")
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
                    JsonParser
                        .parseString(json)
                        .asJsonObject
                        .getAsJsonObject("web")
                        ?.getAsJsonArray("results")
                        ?: return emptyList()
                results.take(limit).mapNotNull { el ->
                    val obj = el.asJsonObject
                    val title = obj.get("title")?.asString ?: return@mapNotNull null
                    val resultUrl = obj.get("url")?.asString ?: return@mapNotNull null
                    val snippet = obj.get("description")?.asString ?: ""
                    WebSearchResult(title = title, url = resultUrl, snippet = snippet)
                }
            }.getOrElse {
                Log.w(TAG, "Failed to parse Brave response: ${it.javaClass.simpleName}")
                emptyList()
            }

        companion object {
            private const val TAG = "BraveSearchService"
        }
    }
