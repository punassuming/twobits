package com.twobits.pricedrop.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.twobits.billing.SubscriptionRepository
import com.twobits.pricedrop.data.local.DebugLogEntry
import com.twobits.pricedrop.data.local.DebugLogEntryType
import com.twobits.pricedrop.data.local.DebugLogStore
import com.twobits.pricedrop.data.provider.AiFeature
import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderMode
import com.twobits.pricedrop.data.provider.ProviderSettingsStore
import com.twobits.pricedrop.data.remote.dto.BarcodeResponseDto
import com.twobits.pricedrop.data.remote.dto.ChatResponseDto
import com.twobits.pricedrop.data.remote.dto.HistoryPointDto
import com.twobits.pricedrop.data.remote.dto.HistoryResponseDto
import com.twobits.pricedrop.data.remote.dto.OfferDto
import com.twobits.pricedrop.data.remote.dto.PriceResponseDto
import com.twobits.pricedrop.data.remote.dto.SearchResponseDto
import com.twobits.pricedrop.data.remote.dto.SearchResultDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls PriceDrop data endpoints. In Pro mode all non-chat requests route through [PRO_BASE_URL]
 * using the RevenueCat app-user ID as the Bearer token (Worker holds all provider keys).
 * In BYOK mode every provider is called directly at its own base URL with the user's key —
 * the Worker is never involved. OpenAI (chat) follows the same rule: Pro → Worker proxy,
 * BYOK → api.openai.com directly.
 */
@Singleton
class PriceDropApiClient
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val gson: Gson,
        private val subscriptionRepository: SubscriptionRepository,
        private val providerSettings: ProviderSettingsStore,
        private val debugLogStore: DebugLogStore,
    ) {
        private val jsonMedia = "application/json; charset=utf-8".toMediaType()

        // ---------------------------------------------------------------------------
        // Public API
        // ---------------------------------------------------------------------------

        suspend fun price(
            asin: String? = null,
            upc: String? = null,
        ): PriceResponseDto =
            when (providerSettings.getMode(PriceDropProvider.RAINFOREST)) {
                ProviderMode.BYOK -> priceDirect(asin, upc, byokKey(PriceDropProvider.RAINFOREST))
                ProviderMode.PRO -> {
                    val body =
                        JsonObject().apply {
                            asin?.takeIf { it.isNotBlank() }?.let { addProperty("asin", it) }
                            upc?.takeIf { it.isNotBlank() }?.let { addProperty("upc", it) }
                        }
                    workerPost("/v1/pricedrop/price", body, PriceResponseDto::class.java, op = "price")
                }
                // Rainforest has no local capability and never offers it in the UI.
                ProviderMode.OFF, ProviderMode.LOCAL -> PriceResponseDto(found = false)
            }

        suspend fun history(asin: String): HistoryResponseDto =
            when (providerSettings.getMode(PriceDropProvider.RAINFOREST)) {
                ProviderMode.BYOK -> historyDirect(asin, byokKey(PriceDropProvider.RAINFOREST))
                ProviderMode.PRO -> {
                    val body = JsonObject().apply { addProperty("asin", asin) }
                    workerPost("/v1/pricedrop/history", body, HistoryResponseDto::class.java, op = "history")
                }
                // Rainforest has no local capability and never offers it in the UI.
                ProviderMode.OFF, ProviderMode.LOCAL -> HistoryResponseDto()
            }

        suspend fun barcode(upc: String): BarcodeResponseDto =
            when (providerSettings.getMode(PriceDropProvider.RAINFOREST)) {
                ProviderMode.BYOK -> barcodeDirect(upc, byokKey(PriceDropProvider.RAINFOREST))
                ProviderMode.PRO -> {
                    val body = JsonObject().apply { addProperty("upc", upc) }
                    workerPost("/v1/pricedrop/barcode", body, BarcodeResponseDto::class.java, op = "barcode")
                }
                // Rainforest has no local capability and never offers it in the UI.
                ProviderMode.OFF, ProviderMode.LOCAL -> BarcodeResponseDto(found = false)
            }

        /**
         * Shopping-scoped chat. Both whether Ask runs at all AND whether it routes through the
         * Worker's OpenAI proxy (Pro) or calls api.openai.com directly (BYOK) come from
         * [AiFeature.ASK]'s own independently-stored Source — never from OpenAI's shared
         * provider mode, which extractProductFromPage() (Product search's URL-paste extraction)
         * also reads; branching on the shared mode here would let toggling Ask silently change
         * Search's routing too. The BYOK API key itself is still the one shared OpenAI
         * credential — only the on/off/Pro-vs-BYOK decision is Ask-specific.
         * [history] is the full conversation (all user + assistant turns) including the new user
         * message as the last entry; the system prompt is prepended here. Model is resolved from
         * the user's AI Config selection for [AiFeature.ASK].
         */
        suspend fun chat(
            systemPrompt: String,
            history: List<com.twobits.pricedrop.ui.ask.ChatMessage>,
        ): String {
            val askSource = providerSettings.getFeatureSource(AiFeature.ASK)
            if (askSource == ProviderMode.OFF) {
                throw IOException("Ask assistant is turned off. Enable it with a BYOK key or Pro in Settings.")
            }
            // LOCAL routes through AskViewModel's own LocalAskSession before reaching here —
            // this function is the cloud (BYOK/Pro) path only.
            if (askSource == ProviderMode.LOCAL) {
                throw IllegalStateException("chat() does not handle ProviderMode.LOCAL — route through LocalAskSession instead.")
            }
            val isProMode = askSource == ProviderMode.PRO
            val baseUrl =
                if (isProMode) {
                    PRO_BASE_URL
                } else {
                    PriceDropProvider.OPENAI.byokBaseUrl.trimEnd('/')
                }
            val authHeader =
                if (isProMode) {
                    "Bearer ${subscriptionRepository.getAppUserId()}"
                } else {
                    "Bearer ${byokKey(PriceDropProvider.OPENAI)}"
                }
            val selectedModel = providerSettings.getFeatureModel(AiFeature.ASK)
            val model = selectedModel.ifBlank { if (isProMode) PRO_CHAT_MODEL else BYOK_CHAT_MODEL }
            val webSearchEnabledForAsk = PriceDropProvider.WEB_SEARCH.key in providerSettings.getFeatureProviders(AiFeature.ASK)
            val groundingContext =
                if (isByok(PriceDropProvider.WEB_SEARCH) && webSearchEnabledForAsk) {
                    val userQuery = history.lastOrNull { it.role == "user" }?.content.orEmpty()
                    if (userQuery.isNotBlank()) {
                        runCatching {
                            val results =
                                searchDirectJina(
                                    userQuery.take(200),
                                    3,
                                    providerSettings.getKey(PriceDropProvider.WEB_SEARCH),
                                )
                            if (results.results.isEmpty()) {
                                ""
                            } else {
                                "\n\nLive web context:\n" +
                                    results.results
                                        .mapIndexed { i, r -> "${i + 1}. ${r.title} — ${r.url}" }
                                        .joinToString("\n")
                            }
                        }.getOrDefault("")
                    } else {
                        ""
                    }
                } else {
                    ""
                }
            val messages =
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("role", "system")
                            addProperty("content", systemPrompt + groundingContext)
                        },
                    )
                    history.forEach { msg ->
                        add(
                            JsonObject().apply {
                                addProperty("role", msg.role)
                                addProperty("content", msg.content)
                            },
                        )
                    }
                }
            val body =
                JsonObject().apply {
                    addProperty("model", model)
                    add("messages", messages)
                }
            val dto =
                post(
                    "$baseUrl/v1/chat/completions",
                    body,
                    ChatResponseDto::class.java,
                    authHeader,
                    op = "chat",
                    logType = DebugLogEntryType.AI_CALL,
                )
            return dto.choices
                .firstOrNull()
                ?.message
                ?.content
                .orEmpty()
        }

        // ---------------------------------------------------------------------------
        // BYOK — direct provider calls
        // ---------------------------------------------------------------------------

        private suspend fun priceDirect(
            asin: String?,
            upc: String?,
            apiKey: String,
        ): PriceResponseDto =
            withContext(Dispatchers.IO) {
                val startedAtMs = System.currentTimeMillis()
                val urlBuilder =
                    "https://api.rainforestapi.com/request"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("api_key", apiKey)
                if (asin != null) {
                    urlBuilder.addQueryParameter("type", "product")
                    urlBuilder.addQueryParameter("asin", asin)
                } else {
                    urlBuilder.addQueryParameter("type", "search")
                    urlBuilder.addQueryParameter("keywords", upc!!)
                }
                val request =
                    Request
                        .Builder()
                        .url(urlBuilder.build())
                        .get()
                        .build()
                runCatching {
                    client.newCall(request).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IOException(friendlyError(response.code, text))
                        val data = gson.fromJson(text, JsonObject::class.java)
                        val product =
                            data["product"]?.asJsonObject
                                ?: data["search_results"]
                                    ?.asJsonArray
                                    ?.firstOrNull()
                                    ?.asJsonObject
                                    ?.get("product")
                                    ?.asJsonObject
                                ?: return@use PriceResponseDto(found = false)

                        val listings = product["sellers_results"]?.asJsonObject?.get("listings")?.asJsonArray ?: JsonArray()
                        val offers =
                            listings
                                .mapNotNull { listing ->
                                    val l = listing.asJsonObject
                                    val price = l["price"]?.asJsonObject?.get("value")?.let { runCatching { it.asDouble }.getOrNull() } ?: return@mapNotNull null
                                    OfferDto(
                                        seller = l["seller"]?.asJsonObject?.get("name")?.asString ?: "",
                                        price = price,
                                        shipping = l["shipping_charge"]?.asJsonObject?.get("value")?.let { runCatching { it.asDouble }.getOrNull() } ?: 0.0,
                                        availability = l["availability"]?.asJsonObject?.get("type")?.asString ?: "unknown",
                                        url = l["link"]?.asString ?: product["link"]?.asString ?: "",
                                    )
                                }.take(10)

                        PriceResponseDto(
                            found = true,
                            title = product["title"]?.asString,
                            asin = product["asin"]?.asString,
                            price =
                                product["buybox_winner"]
                                    ?.asJsonObject
                                    ?.get("price")
                                    ?.asJsonObject
                                    ?.get("value")
                                    ?.let { runCatching { it.asDouble }.getOrNull() },
                            currency =
                                product["buybox_winner"]
                                    ?.asJsonObject
                                    ?.get("price")
                                    ?.asJsonObject
                                    ?.get("currency")
                                    ?.asString ?: "USD",
                            availability =
                                product["buybox_winner"]
                                    ?.asJsonObject
                                    ?.get("availability")
                                    ?.asJsonObject
                                    ?.get("type")
                                    ?.asString ?: "unknown",
                            url = product["link"]?.asString,
                            offers = offers,
                        )
                    }
                }.onSuccess {
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "price",
                            endpoint = "rainforest",
                            success = true,
                            responseSnippet = if (it.found) "found" else "not found",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                        ),
                    )
                }.onFailure { e ->
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "price",
                            endpoint = "rainforest",
                            success = false,
                            responseSnippet = "${e.javaClass.simpleName}: ${e.message}",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                            stackTrace = e.stackTraceToString(),
                        ),
                    )
                }.getOrThrow()
            }

        // Mirrors the worker's pdHistory: Rainforest already provides Amazon price history
        // alongside product data, eliminating the need for a separate Keepa subscription.
        private suspend fun historyDirect(
            asin: String,
            apiKey: String,
        ): HistoryResponseDto =
            withContext(Dispatchers.IO) {
                val startedAtMs = System.currentTimeMillis()
                val url =
                    "https://api.rainforestapi.com/request"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("api_key", apiKey)
                        .addQueryParameter("type", "product")
                        .addQueryParameter("asin", asin)
                        .addQueryParameter("include_fields", "product.price_history")
                        .build()
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build()
                runCatching {
                    client.newCall(request).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IOException(friendlyError(response.code, text))
                        val data = gson.fromJson(text, JsonObject::class.java)
                        val product = data["product"]?.asJsonObject ?: return@use HistoryResponseDto()

                        val rawHistory = product["price_history"]?.asJsonArray ?: JsonArray()
                        val history =
                            rawHistory.mapNotNull { entry ->
                                val e = entry.asJsonObject
                                val price = e["price"]?.let { runCatching { it.asDouble }.getOrNull() } ?: return@mapNotNull null
                                val ts = e["date"]?.asString?.let { parseRainforestDate(it) } ?: return@mapNotNull null
                                HistoryPointDto(ts = ts, price = price)
                            }
                        val lowestPrice = history.minOfOrNull { it.price }
                        HistoryResponseDto(history = history, lowestPrice = lowestPrice)
                    }
                }.onSuccess {
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "history",
                            endpoint = "rainforest",
                            success = true,
                            responseSnippet = "${it.history.size} points",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                        ),
                    )
                }.onFailure { e ->
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "history",
                            endpoint = "rainforest",
                            success = false,
                            responseSnippet = "${e.javaClass.simpleName}: ${e.message}",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                            stackTrace = e.stackTraceToString(),
                        ),
                    )
                }.getOrThrow()
            }

        // Rainforest's price_history dates have been observed as ISO-8601 ("2024-01-15" or
        // full instants); try both and skip entries that don't parse rather than guessing.
        private fun parseRainforestDate(raw: String): Long? =
            runCatching {
                java.time.Instant
                    .parse(raw)
                    .toEpochMilli()
            }.recoverCatching {
                java.time.LocalDate
                    .parse(raw)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()

        private suspend fun barcodeDirect(
            upc: String,
            apiKey: String,
        ): BarcodeResponseDto =
            withContext(Dispatchers.IO) {
                val startedAtMs = System.currentTimeMillis()
                val url =
                    "https://api.rainforestapi.com/request"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("api_key", apiKey)
                        .addQueryParameter("type", "search")
                        .addQueryParameter("keywords", upc)
                        .addQueryParameter("page", "1")
                        .build()
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build()
                runCatching {
                    client.newCall(request).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IOException(friendlyError(response.code, text))
                        val data = gson.fromJson(text, JsonObject::class.java)
                        val product =
                            data["search_results"]
                                ?.asJsonArray
                                ?.firstOrNull()
                                ?.asJsonObject
                                ?.get("product")
                                ?.asJsonObject
                                ?: return@use BarcodeResponseDto(found = false)
                        BarcodeResponseDto(
                            found = true,
                            title = product["title"]?.asString,
                            asin = product["asin"]?.asString,
                            imageUrl = product["image"]?.asString,
                            price = product["price"]?.asJsonObject?.get("value")?.let { runCatching { it.asDouble }.getOrNull() },
                            url = product["link"]?.asString,
                        )
                    }
                }.onSuccess {
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "barcode",
                            endpoint = "rainforest",
                            success = true,
                            responseSnippet = if (it.found) "found" else "not found",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                        ),
                    )
                }.onFailure { e ->
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "barcode",
                            endpoint = "rainforest",
                            success = false,
                            responseSnippet = "${e.javaClass.simpleName}: ${e.message}",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                            stackTrace = e.stackTraceToString(),
                        ),
                    )
                }.getOrThrow()
            }

        // ---------------------------------------------------------------------------
        // Jina AI — web search + page reading (WEB_SEARCH BYOK)
        // ---------------------------------------------------------------------------

        private suspend fun searchDirectJina(
            query: String,
            maxResults: Int,
            apiKey: String,
        ): SearchResponseDto =
            withContext(Dispatchers.IO) {
                val startedAtMs = System.currentTimeMillis()
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
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Accept", "application/json")
                        .addHeader("X-Return-Format", "json")
                        .get()
                        .build()
                runCatching {
                    client.newCall(request).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IOException(friendlyError(response.code, text))
                        val data = gson.fromJson(text, JsonObject::class.java)
                        val results =
                            (data.getAsJsonArray("data") ?: JsonArray())
                                .take(maxResults)
                                .map { item ->
                                    val r = item.asJsonObject
                                    val itemUrl = r["url"]?.asString
                                    SearchResultDto(
                                        title = r["title"]?.asString,
                                        price = null,
                                        source = runCatching { itemUrl?.toHttpUrl()?.host }.getOrNull(),
                                        url = itemUrl,
                                    )
                                }
                        SearchResponseDto(results)
                    }
                }.onSuccess {
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "web-search",
                            endpoint = "jina",
                            requestSummary = query,
                            success = true,
                            responseSnippet = "${it.results.size} results",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                        ),
                    )
                }.onFailure { e ->
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "web-search",
                            endpoint = "jina",
                            requestSummary = query,
                            success = false,
                            responseSnippet = "${e.javaClass.simpleName}: ${e.message}",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                            stackTrace = e.stackTraceToString(),
                        ),
                    )
                }.getOrThrow()
            }

        /**
         * Reads a product page via the active reader provider ([ProviderSettingsStore.getPageReaderProvider])
         * when it's in BYOK mode. Returns empty string in Pro mode (the Worker handles page reading
         * server-side — Firecrawl has no Worker route, so this always returns "" for it in Pro) or on error.
         */
        suspend fun readPage(url: String): String {
            val reader = providerSettings.getPageReaderProvider()
            if (!isByok(reader)) return ""
            return when (reader) {
                PriceDropProvider.FIRECRAWL -> readPageFirecrawl(url, providerSettings.getKey(PriceDropProvider.FIRECRAWL))
                else -> readPageDirect(url, providerSettings.getKey(PriceDropProvider.WEB_SEARCH))
            }
        }

        private suspend fun readPageDirect(
            url: String,
            apiKey: String,
        ): String =
            withContext(Dispatchers.IO) {
                val startedAtMs = System.currentTimeMillis()
                runCatching {
                    val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                    val request =
                        Request
                            .Builder()
                            .url("https://r.jina.ai/$encodedUrl")
                            .addHeader("Authorization", "Bearer $apiKey")
                            .get()
                            .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use ""
                        response.body?.string().orEmpty()
                    }
                }.onSuccess {
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "jina-read",
                            endpoint = "jina-reader",
                            requestSummary = url,
                            success = it.isNotEmpty(),
                            responseSnippet = "${it.length} chars",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                        ),
                    )
                }.onFailure { e ->
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "jina-read",
                            endpoint = "jina-reader",
                            requestSummary = url,
                            success = false,
                            responseSnippet = "${e.javaClass.simpleName}: ${e.message}",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                        ),
                    )
                }.getOrDefault("")
            }

        private suspend fun readPageFirecrawl(
            url: String,
            apiKey: String,
        ): String =
            withContext(Dispatchers.IO) {
                val startedAtMs = System.currentTimeMillis()
                runCatching {
                    val body =
                        JsonObject()
                            .apply {
                                addProperty("url", url)
                                add("formats", JsonArray().apply { add("markdown") })
                            }.toString()
                            .toRequestBody(jsonMedia)
                    val request =
                        Request
                            .Builder()
                            .url("https://api.firecrawl.dev/v2/scrape")
                            .addHeader("Authorization", "Bearer $apiKey")
                            .addHeader("Content-Type", "application/json")
                            .post(body)
                            .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use ""
                        parseFirecrawlMarkdown(response.body?.string().orEmpty())
                    }
                }.onSuccess {
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "firecrawl-read",
                            endpoint = "firecrawl-reader",
                            requestSummary = url,
                            success = it.isNotEmpty(),
                            responseSnippet = "${it.length} chars",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                        ),
                    )
                }.onFailure { e ->
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = DebugLogEntryType.SERVICE_CALL,
                            op = "firecrawl-read",
                            endpoint = "firecrawl-reader",
                            requestSummary = url,
                            success = false,
                            responseSnippet = "${e.javaClass.simpleName}: ${e.message}",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                        ),
                    )
                }.getOrDefault("")
            }

        /**
         * `{"success": true, "data": {"markdown": "...", "metadata": {...}}}` — confirmed against
         * both official SDKs' own response parsing (js-sdk's `res.data.data`, python-sdk's
         * `body["data"]`) and Firecrawl's docs. The top-level fallback guards only a future API
         * change, not present ambiguity.
         */
        private fun parseFirecrawlMarkdown(json: String): String =
            runCatching {
                val root = gson.fromJson(json, JsonObject::class.java)
                (root.getAsJsonObject("data")?.get("markdown") ?: root.get("markdown"))?.asString.orEmpty()
            }.getOrDefault("")

        /**
         * Uses OpenAI to extract product title and price from Jina-read page content.
         * Falls back to ("Product from URL", null) on any parsing or network error.
         */
        suspend fun extractProductFromPage(
            pageContent: String,
            @Suppress("UNUSED_PARAMETER") url: String,
        ): Pair<String, Double?> {
            if (pageContent.isBlank()) return "Product from URL" to null
            val isProMode = !isByok(PriceDropProvider.OPENAI)
            val baseUrl =
                if (isProMode) PRO_BASE_URL else PriceDropProvider.OPENAI.byokBaseUrl.trimEnd('/')
            val authHeader =
                if (isProMode) {
                    "Bearer ${subscriptionRepository.getAppUserId()}"
                } else {
                    "Bearer ${byokKey(PriceDropProvider.OPENAI)}"
                }
            val selectedModel = providerSettings.getFeatureModel(AiFeature.SEARCH)
            val model = selectedModel.ifBlank { if (isProMode) PRO_CHAT_MODEL else BYOK_CHAT_MODEL }
            return runCatching {
                val body =
                    JsonObject().apply {
                        addProperty("model", model)
                        add(
                            "messages",
                            JsonArray().apply {
                                add(
                                    JsonObject().apply {
                                        addProperty("role", "system")
                                        addProperty(
                                            "content",
                                            "Extract the product name and current price from this page content. " +
                                                "Respond with ONLY valid JSON: " +
                                                "{\"title\":\"...\",\"price\":number_or_null}",
                                        )
                                    },
                                )
                                add(
                                    JsonObject().apply {
                                        addProperty("role", "user")
                                        addProperty("content", pageContent.take(3_000))
                                    },
                                )
                            },
                        )
                    }
                val dto =
                    post(
                        "$baseUrl/v1/chat/completions",
                        body,
                        ChatResponseDto::class.java,
                        authHeader,
                        op = "extract-product",
                        logType = DebugLogEntryType.AI_CALL,
                    )
                val json =
                    gson.fromJson(
                        dto.choices
                            .firstOrNull()
                            ?.message
                            ?.content
                            .orEmpty(),
                        JsonObject::class.java,
                    )
                val title = json["title"]?.asString?.takeIf { it.isNotBlank() } ?: "Product from URL"
                val price = json["price"]?.let { runCatching { it.asDouble }.getOrNull() }
                title to price
            }.getOrDefault("Product from URL" to null)
        }

        // ---------------------------------------------------------------------------
        // Helpers
        // ---------------------------------------------------------------------------

        private suspend fun isByok(provider: PriceDropProvider): Boolean = providerSettings.getMode(provider) == ProviderMode.BYOK

        /**
         * The stored key for [provider], required to be non-blank. Call only after [isByok] is
         * true for the same provider — a user can flip a provider's mode to BYOK without saving a
         * key yet, and without this guard that reaches the upstream provider with an empty key,
         * surfacing as a confusing raw HTTP error instead of a clear "add your key" message.
         */
        private suspend fun byokKey(provider: PriceDropProvider): String {
            val key = providerSettings.getKey(provider)
            if (key.isBlank()) {
                throw IOException("Add your ${provider.displayName} key in Settings, or switch it back to Pro.")
            }
            return key
        }

        /** Pro-mode Worker call. Auth is always the RevenueCat App User ID. */
        private suspend fun <T> workerPost(
            path: String,
            body: JsonObject,
            type: Class<T>,
            op: String? = null,
        ): T = post("$PRO_BASE_URL$path", body, type, "Bearer ${subscriptionRepository.getAppUserId()}", op = op)

        private suspend fun <T> post(
            url: String,
            body: JsonObject,
            type: Class<T>,
            authHeader: String,
            op: String? = null,
            logType: DebugLogEntryType = DebugLogEntryType.SERVICE_CALL,
        ): T =
            withContext(Dispatchers.IO) {
                val startedAtMs = System.currentTimeMillis()
                val builder =
                    Request
                        .Builder()
                        .url(url)
                        .addHeader("Authorization", authHeader)
                        .addHeader("X-TwoBits-App", "pricedrop")
                        .addHeader("Content-Type", "application/json")
                        .post(gson.toJson(body).toRequestBody(jsonMedia))
                if (op != null) builder.addHeader("X-TwoBits-Op", op)
                val request = builder.build()
                runCatching {
                    client.newCall(request).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw IOException(friendlyError(response.code, text))
                        }
                        gson.fromJson(text, type)
                    }
                }.onSuccess {
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = logType,
                            op = op ?: "post",
                            endpoint = url,
                            success = true,
                            durationMs = System.currentTimeMillis() - startedAtMs,
                        ),
                    )
                }.onFailure { e ->
                    debugLogStore.record(
                        DebugLogEntry(
                            timestampMs = System.currentTimeMillis(),
                            type = logType,
                            op = op ?: "post",
                            endpoint = url,
                            success = false,
                            responseSnippet = "${e.javaClass.simpleName}: ${e.message}",
                            durationMs = System.currentTimeMillis() - startedAtMs,
                            stackTrace = e.stackTraceToString(),
                        ),
                    )
                }.getOrThrow()
            }

        private fun friendlyError(
            code: Int,
            body: String,
        ): String =
            when (code) {
                401 -> "Sign-in required to use PriceDrop connectors."
                403 -> "PriceDrop Pro subscription required."
                429 -> "Monthly usage limit reached. Try again next month or add your own keys."
                in 500..599 -> "The price service is temporarily unavailable. Try again shortly."
                else ->
                    runCatching { gson.fromJson(body, JsonObject::class.java)?.get("error")?.asString }
                        .getOrNull() ?: "Request failed ($code)."
            }

        companion object {
            const val PRO_BASE_URL = "https://api.twobits.app"
            private const val PRO_CHAT_MODEL = "gpt-5-mini"
            private const val BYOK_CHAT_MODEL = "gpt-4o-mini"
        }
    }
