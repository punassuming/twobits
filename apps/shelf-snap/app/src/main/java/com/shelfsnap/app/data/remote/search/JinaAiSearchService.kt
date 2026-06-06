package com.shelfsnap.app.data.remote.search

import android.util.Log
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
 * [WebSearchService] backed by Jina AI Search (s.jina.ai).
 * Keyless — no API key required for basic queries.
 */
@Singleton
class JinaAiSearchService @Inject constructor() : WebSearchService {

    override val provider = SearchProvider.JINA

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override suspend fun search(query: String, apiKey: String, limit: Int): List<WebSearchResult> =
        withContext(Dispatchers.IO) {
            val url = "https://s.jina.ai/".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Jina AI search failed: HTTP ${response.code}")
                    return@use emptyList()
                }
                parse(body, limit)
            }
        }

    private fun parse(json: String, limit: Int): List<WebSearchResult> = runCatching {
        val data = JsonParser.parseString(json).asJsonObject
            .getAsJsonArray("data") ?: return emptyList()
        data.take(limit).mapNotNull { el ->
            val obj = el.asJsonObject
            val title = obj.get("title")?.asString ?: return@mapNotNull null
            val resultUrl = obj.get("url")?.asString ?: return@mapNotNull null
            val snippet = obj.get("description")?.asString
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
