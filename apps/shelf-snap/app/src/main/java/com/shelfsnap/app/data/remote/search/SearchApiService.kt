package com.shelfsnap.app.data.remote.search

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
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
 * `site:`, so the platform-targeted queries ("…site:ebay.com/itm sold" or "…for sale")
 * return real marketplace listings with usable links. eBay-targeted queries use the
 * dedicated eBay engine; its hard sold_listings filter is applied only for the
 * "sold"-intent query, so the "for sale" one still gets active listings back.
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

                val isEbaySold = isEbaySearchQuery(query) && isSoldIntentQuery(query)

                val request =
                    Request
                        .Builder()
                        .url(buildSearchApiUrl(query, limit))
                        .addHeader("Accept", "application/json")
                        .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                        .get()
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "SearchAPI.io search failed: HTTP ${response.code}")
                        throw IOException("SearchAPI.io search failed: HTTP ${response.code}")
                    }
                    parseSearchApiResponse(response.body?.string().orEmpty(), limit, soldOnly = isEbaySold)
                }
            }

        private companion object {
            const val TAG = "SearchApiService"
        }
    }

internal fun buildSearchApiUrl(
    query: String,
    limit: Int,
): HttpUrl {
    val builder =
        "https://www.searchapi.io/api/v1/search"
            .toHttpUrl()
            .newBuilder()
    if (isEbaySearchQuery(query)) {
        val ebayQuery =
            query
                .replace(Regex("site:\\S+", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\bsold\\b", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\bfor sale\\b", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        builder.addQueryParameter("engine", "ebay_search")
        builder.addQueryParameter("q", ebayQuery.ifBlank { query })
        // Only the sold-intent query asks for eBay's completed-sales filter — the for-sale
        // variant needs the engine's normal (active-listing) results, so filters is omitted
        // rather than set to some "active" value that may not exist as a real filter option.
        if (isSoldIntentQuery(query)) {
            builder.addQueryParameter("filters", "sold_listings")
        }
    } else {
        builder.addQueryParameter("engine", "google")
        builder.addQueryParameter("q", query)
        builder.addQueryParameter("num", limit.coerceIn(1, 20).toString())
    }
    return builder.build()
}

internal fun parseSearchApiResponse(
    json: String,
    limit: Int,
    soldOnly: Boolean,
): List<WebSearchResult> =
    runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        val results =
            root.getAsJsonArray("organic_results")
                ?: root.getAsJsonArray("shopping_results")
                ?: root.getAsJsonArray("inline_shopping")
                ?: return emptyList()
        results.take(limit).mapNotNull { element ->
            val result = element.asJsonObject
            val title = result.string("title") ?: return@mapNotNull null
            val resultUrl =
                result.string("link")
                    ?: result.string("product_link")
                    ?: result.string("url")
                    ?: return@mapNotNull null
            val priceText = result.string("price")
            val price =
                result.number("extracted_price")
                    ?: result
                        .getAsJsonObject("extracted_price_range")
                        ?.number("from")
                    ?: priceText?.let(::parsePrice)
            val rawSnippet = result.string("snippet") ?: result.string("description")
            // items_sold is a historical unit-sales count ("10 sold") shown on active listings
            // too — it says nothing about whether THIS listing has ended, so it must not feed
            // into sold-status inference (only title/snippet, which may genuinely say
            // "SOLD"/"completed" for an ended listing).
            val sold =
                when {
                    soldOnly -> true
                    price != null && listOf(title, rawSnippet).any(::containsSoldWord) -> true
                    else -> null
                }
            val snippet =
                listOfNotNull(
                    rawSnippet,
                    priceText?.let { "Price: $it" },
                    result.string("condition")?.let { "Condition: $it" },
                    if (soldOnly) "Completed/sold listing" else result.string("items_sold"),
                ).distinct().joinToString(" · ")
            WebSearchResult(
                title = title,
                url = resultUrl,
                snippet = snippet,
                platformKey = if (soldOnly) "ebay" else marketplaceKeyFromUrl(resultUrl),
                price = price,
                sold = sold,
                date = result.string("date") ?: result.string("ended") ?: "",
            )
        }
    }.getOrElse {
        Log.w("SearchApiService", "Failed to parse SearchAPI.io response: ${it.javaClass.simpleName}")
        emptyList()
    }

private fun isEbaySearchQuery(query: String): Boolean = query.contains("ebay.com", ignoreCase = true)

/**
 * Whether [query] is one of [PriceResearchService][com.shelfsnap.app.data.remote.PriceResearchService]'s
 * "sold" (as opposed to "for sale") core queries, detected from the literal trailing keyword.
 * Drives whether the eBay engine's hard sold_listings filter is applied — the for-sale variant
 * needs the engine's normal active-listing results.
 */
private fun isSoldIntentQuery(query: String): Boolean = Regex("\\bsold\\b", RegexOption.IGNORE_CASE).containsMatchIn(query)

private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonObject.number(name: String): Double? =
    get(name)
        ?.takeIf { it.isJsonPrimitive }
        ?.let { runCatching { it.asDouble }.getOrNull() }

private fun parsePrice(text: String): Double? =
    PRICE_PATTERN
        .find(text.replace(",", ""))
        ?.groupValues
        ?.getOrNull(1)
        ?.toDoubleOrNull()

private fun containsSoldWord(text: String?): Boolean = text?.contains(SOLD_PATTERN) == true

private val PRICE_PATTERN = Regex("(?:US\\s*)?[$]\\s*([0-9]+(?:\\.[0-9]{1,2})?)")
private val SOLD_PATTERN = Regex("\\b(sold|completed)\\b", RegexOption.IGNORE_CASE)
