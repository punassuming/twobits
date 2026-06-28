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
 * SearchAPI.io web search. Unlike Jina's s.jina.ai (which silently ignores `site:`
 * operators and returns error pages for eBay), SearchAPI.io's Google engine honors
 * `site:`, so the platform-targeted queries ("…site:ebay.com/itm sold") return real
 * marketplace listings with usable links. eBay-targeted queries use the dedicated
 * eBay engine for structured sold-listing results.
 */
@Singleton
class SearchApiService
    @Inject
    constructor() : WebSearchService {
        override val provider = SearchProvider.SEARCHAPI

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
                    throw IOException("SearchAPI.io requires an API key — add one in Settings")
                }

                val isEbay = query.lowercase().contains("ebay.com")
                val ebayQuery =
                    query
                        .replace(Regex("site:\\S+", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("\\bsold\\b", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("\\s+"), " ")
                        .trim()

                val builder =
                    "https://www.searchapi.io/api/v1/search"
                        .toHttpUrl()
                        .newBuilder()
                if (isEbay) {
                    builder.addQueryParameter("engine", "ebay")
                    builder.addQueryParameter("q", ebayQuery.ifBlank { query })
                } else {
                    builder.addQueryParameter("engine", "google")
                    builder.addQueryParameter("q", query)
                    builder.addQueryParameter("num", limit.coerceIn(1, 20).toString())
                }
                builder.addQueryParameter("api_key", apiKey.trim())

                val request =
                    Request
                        .Builder()
                        .url(builder.build())
                        .addHeader("Accept", "application/json")
                        .get()
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "SearchAPI.io search failed: HTTP ${response.code}")
                        throw IOException("SearchAPI.io search failed: HTTP ${response.code}")
                    }
                    parse(response.body?.string().orEmpty(), limit)
                }
            }

        private fun parse(
            json: String,
            limit: Int,
        ): List<WebSearchResult> =
            runCatching {
                val root = JsonParser.parseString(json).asJsonObject
                val results =
                    root.getAsJsonArray("organic_results")
                        ?: root.getAsJsonArray("shopping_results")
                        ?: return emptyList()
                results.take(limit).mapNotNull { el ->
                    val obj = el.asJsonObject
                    val title = obj.get("title")?.asString ?: return@mapNotNull null
                    val resultUrl =
                        (obj.get("link") ?: obj.get("product_link"))
                            ?.takeIf { it.isJsonPrimitive }
                            ?.asString ?: return@mapNotNull null
                    val snippet =
                        obj.get("snippet")?.takeIf { it.isJsonPrimitive }?.asString
                            ?: obj
                                .get("price")
                                ?.takeIf { it.isJsonPrimitive }
                                ?.asString
                                ?.let { "Price: $it" }
                            ?: ""
                    WebSearchResult(title = title, url = resultUrl, snippet = snippet)
                }
            }.getOrElse {
                Log.w(TAG, "Failed to parse SearchAPI.io response: ${it.javaClass.simpleName}")
                emptyList()
            }

        private companion object {
            const val TAG = "SearchApiService"
        }
    }
