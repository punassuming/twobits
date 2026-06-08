package com.shelfsnap.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shelfsnap.app.data.model.Citation
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.MarketComp
import com.shelfsnap.app.data.model.MarketResearch
import com.shelfsnap.app.data.model.Platform
import com.shelfsnap.app.data.remote.search.SearchProvider
import com.shelfsnap.app.data.remote.search.WebSearchResolver
import com.shelfsnap.app.data.remote.search.WebSearchResult
import com.shelfsnap.app.util.ApiKeyValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a price-research run; [error] is non-null on failure. */
data class PriceResearchResult(
    val research: MarketResearch = MarketResearch(),
    /** Suggested overall asking price (USD), or null if not produced. */
    val suggestedValue: Double? = null,
    val error: String? = null,
    /** True when live web-search evidence was available; false means AI training data only. */
    val hasWebEvidence: Boolean = false,
)

/**
 * Produces resale price guidance for an item.
 *
 * Pipeline:
 *  1. (optional) gather comparable-listing evidence via the user's [WebSearchService]
 *     (Brave / DuckDuckGo), gated by a search API key.
 *  2. ask the OpenAI Chat Completions API to synthesize the evidence (plus the item's
 *     own attributes) into per-platform suggested prices, comparable comps, and cited
 *     sources as structured JSON.
 *
 * Web search is best-effort: if it's disabled or fails, the model still produces an
 * estimate from its own knowledge, and the result is labelled accordingly.
 */
@Singleton
class PriceResearchService @Inject constructor(
    private val searchResolver: WebSearchResolver
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val json = "application/json; charset=utf-8".toMediaType()

    /**
     * Researches [item]'s resale value.
     *
     * @param openAiKey OpenAI API key (required).
     * @param searchProvider which web-search backend to use for evidence (may be NONE).
     * @param searchKey API key for the search provider (blank for keyless/NONE).
     * @param model OpenAI model to use for price synthesis; defaults to [MODEL].
     */
    suspend fun research(
        item: Item,
        openAiKey: String,
        searchProvider: SearchProvider,
        searchKey: String,
        model: String = MODEL,
    ): PriceResearchResult = withContext(Dispatchers.IO) {
        if (!ApiKeyValidator.isValid(openAiKey)) {
            return@withContext PriceResearchResult(error = ERROR_INVALID_KEY)
        }

        // Step 1 — best-effort web evidence.
        val evidence = gatherEvidence(item, searchProvider, searchKey)

        // Step 2 — synthesize via the model.
        runCatching {
            val requestBody = buildRequest(item, evidence, model)
            val endpoint = if (isResponsesModel(model)) "v1/responses" else "v1/chat/completions"
            val request = Request.Builder()
                .url("https://api.openai.com/$endpoint")
                .addHeader("Authorization", "Bearer $openAiKey")
                .addHeader("Content-Type", "application/json")
                .post(gson.toJson(requestBody).toRequestBody(json))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Pricing request failed: HTTP ${response.code}")
                    return@use PriceResearchResult(error = friendlyHttpError(response.code))
                }
                parseResponse(body, evidence, isResponsesModel(model))
            }
        }.getOrElse { e ->
            Log.w(TAG, "Pricing request threw ${e.javaClass.simpleName}")
            PriceResearchResult(error = friendlyNetworkError(e))
        }
    }

    private suspend fun gatherEvidence(
        item: Item,
        provider: SearchProvider,
        searchKey: String
    ): List<WebSearchResult> {
        val service = searchResolver.resolve(provider) ?: return emptyList()
        val query = buildSearchQuery(item)
        return runCatching { service.search(query, searchKey) }
            .getOrElse {
                Log.w(TAG, "Web search failed: ${it.javaClass.simpleName}")
                emptyList()
            }
    }

    private fun buildSearchQuery(item: Item): String =
        listOf(item.brand, item.model, item.category, "resale price sold")
            .filter { it.isNotBlank() }
            .joinToString(" ")

    private fun buildRequest(item: Item, evidence: List<WebSearchResult>, model: String = MODEL): JsonObject {
        val platformKeys = Platform.entries.joinToString(", ") { it.key }
        val systemPrompt = """
            You are a reselling price-research assistant. Using the item details and any
            web-search evidence provided, estimate fair resale prices for second-hand
            marketplaces and cite your sources. Respond ONLY with valid JSON in this schema:
            {
              "suggestedValue": <number, overall asking price in USD>,
              "averageSoldPrice": <number>,
              "lowPrice": <number>,
              "highPrice": <number>,
              "confidencePercent": <integer 0-100>,
              "suggestedPrices": { "<platformKey>": <number>, ... },
              "comps": [
                { "platform": "<platformKey>", "title": "<listing title>",
                  "price": <number>, "sold": <true|false>, "date": "<recency>",
                  "url": "<source url or empty>" }
              ],
              "citations": [ { "label": "<source>", "url": "<url>" } ]
            }
            Valid platformKey values: $platformKeys.
            Prefer sold listings over active ones. If evidence is thin, lower the
            confidence and say so via fewer comps. Never invent exact URLs you were not given.
        """.trimIndent()

        val userPayload = JsonObject().apply {
            addProperty("category", item.category)
            addProperty("brand", item.brand)
            addProperty("model", item.model)
            addProperty("condition", item.condition.name)
            addProperty("size", item.size)
            addProperty("color", item.color)
            addProperty("quantity", item.quantity)
            addProperty("originalPrice", item.originalPrice)
            addProperty("description", item.description)
            add("tags", JsonArray().apply { item.tags.forEach { add(it) } })
            add("searchEvidence", JsonArray().apply {
                evidence.forEach { r ->
                    add(JsonObject().apply {
                        addProperty("title", r.title)
                        addProperty("url", r.url)
                        addProperty("snippet", r.snippet)
                    })
                }
            })
        }

        val messages = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "system"); addProperty("content", systemPrompt)
            })
            add(JsonObject().apply {
                addProperty("role", "user"); addProperty("content", gson.toJson(userPayload))
            })
        }

        return JsonObject().apply {
            addProperty("model", model)
            val messagesField = if (isResponsesModel(model)) "input" else "messages"
            add(messagesField, messages)
            if (isResponsesModel(model)) addProperty("max_output_tokens", 900)
            else addProperty("max_tokens", 900)
            addProperty("temperature", 0.2)
        }
    }

    private fun isResponsesModel(model: String): Boolean =
        model.startsWith("gpt-5.") || model == "gpt-5"

    private fun parseResponse(
        responseJson: String,
        evidence: List<WebSearchResult>,
        isResponsesApi: Boolean = false,
    ): PriceResearchResult = runCatching {
        val root = JsonParser.parseString(responseJson).asJsonObject
        val content = if (isResponsesApi) {
            root.getAsJsonArray("output").get(0).asJsonObject
                .getAsJsonArray("content").get(0).asJsonObject
                .get("text").asString
        } else {
            root.getAsJsonArray("choices").get(0).asJsonObject
                .getAsJsonObject("message").get("content").asString
        }.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val obj = JsonParser.parseString(content).asJsonObject

        val comps = obj.getAsJsonArray("comps")?.mapNotNull { el ->
            val c = el.asJsonObject
            val platformKey = c.get("platform")?.asString ?: return@mapNotNull null
            if (Platform.fromKey(platformKey) == null) return@mapNotNull null
            MarketComp(
                platformKey = platformKey,
                title = c.get("title")?.asString ?: "",
                price = c.get("price")?.asDouble ?: 0.0,
                sold = c.get("sold")?.asBoolean ?: false,
                date = c.get("date")?.asString ?: "",
                sourceUrl = c.get("url")?.asString ?: ""
            )
        } ?: emptyList()

        val suggestedPrices = obj.getAsJsonObject("suggestedPrices")?.entrySet()
            ?.filter { Platform.fromKey(it.key) != null }
            ?.associate { it.key to it.value.asDouble }
            ?: emptyMap()

        // Citations from the model, plus any evidence URLs it didn't echo back.
        val modelCitations = obj.getAsJsonArray("citations")?.mapNotNull { el ->
            val c = el.asJsonObject
            val label = c.get("label")?.asString ?: return@mapNotNull null
            Citation(label = label, url = c.get("url")?.asString ?: "")
        } ?: emptyList()
        val evidenceCitations = evidence.map { Citation(label = it.title, url = it.url) }
        val citations = (modelCitations + evidenceCitations)
            .distinctBy { it.url.ifBlank { it.label } }
            .take(MAX_CITATIONS)

        val research = MarketResearch(
            comps = comps,
            suggestedPrices = suggestedPrices,
            averageSoldPrice = obj.get("averageSoldPrice")?.asDouble ?: 0.0,
            lowPrice = obj.get("lowPrice")?.asDouble ?: 0.0,
            highPrice = obj.get("highPrice")?.asDouble ?: 0.0,
            confidencePercent = obj.get("confidencePercent")?.asInt ?: 0,
            citations = citations,
            retrievedAt = System.currentTimeMillis()
        )
        PriceResearchResult(
            research = research,
            suggestedValue = obj.get("suggestedValue")?.asDouble,
            hasWebEvidence = evidence.isNotEmpty(),
        )
    }.getOrElse {
        Log.w(TAG, "Failed to parse pricing response: ${it.javaClass.simpleName}")
        PriceResearchResult(error = ERROR_PARSE)
    }

    companion object {
        private const val TAG = "PriceResearchService"

        /**
         * OpenAI model used for price synthesis. A small/cheap reasoning model is
         * sufficient since the heavy lifting is the supplied search evidence. Kept
         * here so it can be swapped in one place as newer mini models ship.
         */
        private const val MODEL = "gpt-4o-mini"

        private const val MAX_CITATIONS = 8

        internal const val ERROR_INVALID_KEY =
            "Invalid or missing OpenAI API key. Check Settings."
        internal const val ERROR_RATE_LIMITED =
            "Rate limited by OpenAI. Wait a moment and try again."
        internal const val ERROR_UNAVAILABLE =
            "Pricing service is temporarily unavailable. Try again shortly."
        internal const val ERROR_MODEL_NOT_FOUND =
            "Selected model isn't available. Try a different model in Settings → AI."
        internal const val ERROR_TIMEOUT =
            "Price research timed out. Check your connection and try again."
        internal const val ERROR_NETWORK =
            "Network error. Check your connection and try again."
        internal const val ERROR_PARSE =
            "Couldn't read the pricing response. Please try again."
        internal const val ERROR_UNKNOWN = "Something went wrong. Please try again."

        internal fun friendlyHttpError(code: Int): String = when {
            code == 401 || code == 403 -> ERROR_INVALID_KEY
            code == 404 -> ERROR_MODEL_NOT_FOUND
            code == 429 -> ERROR_RATE_LIMITED
            code in 500..599 -> ERROR_UNAVAILABLE
            else -> ERROR_UNAVAILABLE
        }

        internal fun friendlyNetworkError(e: Throwable): String = when (e) {
            is SocketTimeoutException -> ERROR_TIMEOUT
            is IOException -> ERROR_NETWORK
            else -> ERROR_UNKNOWN
        }
    }
}
