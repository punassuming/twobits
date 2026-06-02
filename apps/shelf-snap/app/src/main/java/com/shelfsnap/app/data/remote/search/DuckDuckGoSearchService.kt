package com.shelfsnap.app.data.remote.search

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [WebSearchService] backed by the DuckDuckGo Instant Answer API.
 *
 * This endpoint is keyless, so it works without a search API key — handy as a
 * zero-config default. Its coverage is shallower than Brave (it returns related
 * topics rather than a full SERP), but it's enough to surface marketplace links
 * for the model to reason over.
 */
@Singleton
class DuckDuckGoSearchService @Inject constructor() : WebSearchService {

    override val provider = SearchProvider.DUCKDUCKGO

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun search(query: String, apiKey: String, limit: Int): List<WebSearchResult> =
        withContext(Dispatchers.IO) {
            val url = "https://api.duckduckgo.com/".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("format", "json")
                .addQueryParameter("no_html", "1")
                .addQueryParameter("no_redirect", "1")
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "DuckDuckGo search failed: HTTP ${response.code}")
                    return@use emptyList()
                }
                parse(body, limit)
            }
        }

    private fun parse(json: String, limit: Int): List<WebSearchResult> = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        val out = mutableListOf<WebSearchResult>()

        // Abstract (top answer) if present.
        val abstractText = root.get("AbstractText")?.asString.orEmpty()
        val abstractUrl = root.get("AbstractURL")?.asString.orEmpty()
        if (abstractText.isNotBlank() && abstractUrl.isNotBlank()) {
            out += WebSearchResult(
                title = root.get("Heading")?.asString ?: abstractUrl,
                url = abstractUrl,
                snippet = abstractText
            )
        }

        // Flatten RelatedTopics (which may nest under Topics groups).
        root.getAsJsonArray("RelatedTopics")?.let { collectTopics(it, out) }

        out.take(limit)
    }.getOrElse {
        Log.w(TAG, "Failed to parse DuckDuckGo response: ${it.javaClass.simpleName}")
        emptyList()
    }

    private fun collectTopics(arr: JsonArray, out: MutableList<WebSearchResult>) {
        for (el in arr) {
            val obj = el as? JsonObject ?: continue
            when {
                obj.has("Topics") -> collectTopics(obj.getAsJsonArray("Topics"), out)
                obj.has("FirstURL") && obj.has("Text") -> {
                    val resultUrl = obj.get("FirstURL").asString
                    val text = obj.get("Text").asString
                    out += WebSearchResult(title = text, url = resultUrl, snippet = text)
                }
            }
        }
    }

    companion object {
        private const val TAG = "DuckDuckGoSearch"
    }
}
