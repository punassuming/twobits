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

/** What the web-search step produced: results plus the provider and any failure. */
data class SearchEvidence(
    val results: List<WebSearchResult> = emptyList(),
    /** [SearchProvider.key] used, or "" when search was disabled. */
    val providerKey: String = "",
    /** Non-null when the search call itself failed. */
    val error: String? = null,
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
class PriceResearchService
    @Inject
    constructor(
        private val searchResolver: WebSearchResolver,
    ) {
        private val client =
            OkHttpClient
                .Builder()
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
        ): PriceResearchResult =
            withContext(Dispatchers.IO) {
                if (!ApiKeyValidator.isValid(openAiKey)) {
                    return@withContext PriceResearchResult(error = ERROR_INVALID_KEY)
                }

                // Step 1 — best-effort web evidence.
                val evidence = gatherEvidence(item, searchProvider, searchKey)

                // Step 2 — synthesize via the model.
                runCatching {
                    val requestBody = buildRequest(item, evidence, model)
                    val endpoint = if (isResponsesModel(model)) "v1/responses" else "v1/chat/completions"
                    val request =
                        Request
                            .Builder()
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
            searchKey: String,
        ): SearchEvidence {
            val service = searchResolver.resolve(provider) ?: return SearchEvidence()
            val queries = buildSearchQueries(item)
            val seen = mutableSetOf<String>()
            val merged = mutableListOf<WebSearchResult>()
            var lastError: String? = null

            for (query in queries) {
                if (merged.size >= MAX_SEARCH_RESULTS) break
                runCatching { service.search(query, searchKey) }
                    .fold(
                        onSuccess = { results ->
                            results.forEach { r ->
                                if (seen.add(r.url) && merged.size < MAX_SEARCH_RESULTS) merged.add(r)
                            }
                        },
                        onFailure = {
                            Log.w(TAG, "Web search failed for query '$query': ${it.javaClass.simpleName}: ${it.message}")
                            lastError = it.message ?: it.javaClass.simpleName
                        },
                    )
                if (merged.size >= MIN_RESULTS_EARLY_STOP) break
            }

            return if (merged.isNotEmpty()) {
                SearchEvidence(results = merged, providerKey = provider.key)
            } else {
                SearchEvidence(providerKey = provider.key, error = lastError)
            }
        }

        private fun buildSearchQueries(item: Item): List<String> {
            val hasBrandModel = item.brand.isNotBlank() || item.model.isNotBlank()
            val base = listOf(item.brand, item.model).filter { it.isNotBlank() }.joinToString(" ")
            val conditionLabel = item.condition.searchLabel()
            val queries = mutableListOf<String>()

            if (hasBrandModel) {
                // Platform-targeted queries using the top two highest-signal platforms.
                queries.add("$base $conditionLabel ${item.category} site:ebay.com sold".trim())
                queries.add("$base ${item.category} mercari sold listing".trim())
            }
            // Fallback general query (existing behaviour).
            queries.add(
                listOf(item.brand, item.model, item.category, "resale price sold")
                    .filter { it.isNotBlank() }
                    .joinToString(" "),
            )
            return queries
        }

        private fun com.shelfsnap.app.data.model.Condition.searchLabel(): String =
            when (this) {
                com.shelfsnap.app.data.model.Condition.EXCELLENT -> "like new"
                com.shelfsnap.app.data.model.Condition.GOOD -> "good condition"
                com.shelfsnap.app.data.model.Condition.FAIR -> "used"
                com.shelfsnap.app.data.model.Condition.POOR -> "parts or repair"
            }

        private fun buildRequest(
            item: Item,
            evidence: SearchEvidence,
            model: String = MODEL,
        ): JsonObject {
            val platformKeys = Platform.entries.joinToString(", ") { it.key }
            val systemPrompt =
                """
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
                Prefer snippets that contain a price and the word 'sold'. If the search evidence
                does not include actual marketplace listings, lower confidence to ≤ 40 and state
                that in the comp titles.
                """.trimIndent()

            val userPayload =
                JsonObject().apply {
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
                    add(
                        "searchEvidence",
                        JsonArray().apply {
                            evidence.results.forEach { r ->
                                add(
                                    JsonObject().apply {
                                        addProperty("title", r.title)
                                        addProperty("url", r.url)
                                        addProperty("snippet", r.snippet)
                                    },
                                )
                            }
                        },
                    )
                }

            val messages =
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("role", "system")
                            addProperty("content", systemPrompt)
                        },
                    )
                    add(
                        JsonObject().apply {
                            addProperty("role", "user")
                            addProperty("content", gson.toJson(userPayload))
                        },
                    )
                }

            return JsonObject().apply {
                addProperty("model", model)
                if (isResponsesModel(model)) {
                    add("input", messages)
                    // Reasoning tokens count against max_output_tokens; leave headroom so the
                    // model can still emit the full JSON payload after thinking.
                    addProperty("max_output_tokens", 1500)
                    add("reasoning", JsonObject().apply { addProperty("effort", "low") })
                    // gpt-5 reasoning models reject temperature values other than the default.
                } else {
                    add("messages", messages)
                    addProperty("max_tokens", 900)
                    addProperty("temperature", 0.2)
                }
            }
        }

        private fun isResponsesModel(model: String): Boolean = model.startsWith("gpt-5")

        private fun parseResponse(
            responseJson: String,
            evidence: SearchEvidence,
            isResponsesApi: Boolean = false,
        ): PriceResearchResult =
            runCatching {
                val root = JsonParser.parseString(responseJson).asJsonObject
                val content =
                    if (isResponsesApi) {
                        extractResponsesText(root)
                    } else {
                        root
                            .getAsJsonArray("choices")
                            .get(0)
                            .asJsonObject
                            .getAsJsonObject("message")
                            .get("content")
                            .asString
                    }.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

                val obj = JsonParser.parseString(content).asJsonObject

                val comps =
                    obj.getAsJsonArray("comps")?.mapNotNull { el ->
                        val c = el.asJsonObject
                        val platformKey = c.get("platform")?.asString ?: return@mapNotNull null
                        if (Platform.fromKey(platformKey) == null) return@mapNotNull null
                        MarketComp(
                            platformKey = platformKey,
                            title = c.get("title")?.asString ?: "",
                            price = c.get("price")?.asDouble ?: 0.0,
                            sold = c.get("sold")?.asBoolean ?: false,
                            date = c.get("date")?.asString ?: "",
                            sourceUrl = c.get("url")?.asString ?: "",
                        )
                    } ?: emptyList()

                val suggestedPrices =
                    obj
                        .getAsJsonObject("suggestedPrices")
                        ?.entrySet()
                        ?.filter { Platform.fromKey(it.key) != null }
                        ?.associate { it.key to it.value.asDouble }
                        ?: emptyMap()

                // Citations from the model, plus any evidence URLs it didn't echo back.
                val modelCitations =
                    obj.getAsJsonArray("citations")?.mapNotNull { el ->
                        val c = el.asJsonObject
                        val label = c.get("label")?.asString ?: return@mapNotNull null
                        Citation(label = label, url = c.get("url")?.asString ?: "")
                    } ?: emptyList()
                val evidenceCitations = evidence.results.map { Citation(label = it.title, url = it.url) }
                val citations =
                    (modelCitations + evidenceCitations)
                        .distinctBy { it.url.ifBlank { it.label } }
                        .take(MAX_CITATIONS)

                val research =
                    MarketResearch(
                        comps = comps,
                        suggestedPrices = suggestedPrices,
                        averageSoldPrice = obj.get("averageSoldPrice")?.asDouble ?: 0.0,
                        lowPrice = obj.get("lowPrice")?.asDouble ?: 0.0,
                        highPrice = obj.get("highPrice")?.asDouble ?: 0.0,
                        confidencePercent = obj.get("confidencePercent")?.asInt ?: 0,
                        citations = citations,
                        retrievedAt = System.currentTimeMillis(),
                        searchProviderKey = evidence.providerKey,
                        searchResultCount = evidence.results.size,
                        searchError = evidence.error,
                    )
                PriceResearchResult(
                    research = research,
                    suggestedValue = obj.get("suggestedValue")?.asDouble,
                    hasWebEvidence = evidence.results.isNotEmpty(),
                )
            }.getOrElse {
                Log.w(TAG, "Failed to parse pricing response: ${it.javaClass.simpleName}")
                PriceResearchResult(error = ERROR_PARSE)
            }

        /**
         * Pulls the assistant text out of a Responses API payload. The `output` array
         * usually starts with a `reasoning` item for gpt-5 models, so the message item
         * must be located by type rather than by index.
         */
        private fun extractResponsesText(root: JsonObject): String {
            val output = root.getAsJsonArray("output")
            val message =
                output
                    .firstOrNull { el ->
                        el.asJsonObject.get("type")?.asString == "message"
                    }?.asJsonObject ?: error("No message item in Responses output")
            return message
                .getAsJsonArray("content")
                .mapNotNull { part ->
                    val obj = part.asJsonObject
                    if (obj.get("type")?.asString == "output_text") obj.get("text")?.asString else null
                }.joinToString("")
        }

        companion object {
            private const val TAG = "PriceResearchService"

            /**
             * OpenAI model used for price synthesis. A small/cheap reasoning model is
             * sufficient since the heavy lifting is the supplied search evidence. Kept
             * here so it can be swapped in one place as newer mini models ship.
             */
            private const val MODEL = "gpt-5-mini"

            private const val MAX_CITATIONS = 8
            private const val MAX_SEARCH_RESULTS = 12
            private const val MIN_RESULTS_EARLY_STOP = 3

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

            internal fun friendlyHttpError(code: Int): String =
                when {
                    code == 401 || code == 403 -> ERROR_INVALID_KEY
                    code == 404 -> ERROR_MODEL_NOT_FOUND
                    code == 429 -> ERROR_RATE_LIMITED
                    code in 500..599 -> ERROR_UNAVAILABLE
                    else -> ERROR_UNAVAILABLE
                }

            internal fun friendlyNetworkError(e: Throwable): String =
                when (e) {
                    is SocketTimeoutException -> ERROR_TIMEOUT
                    is IOException -> ERROR_NETWORK
                    else -> ERROR_UNKNOWN
                }
        }
    }
