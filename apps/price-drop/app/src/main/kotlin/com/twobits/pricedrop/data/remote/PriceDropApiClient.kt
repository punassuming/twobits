package com.twobits.pricedrop.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.twobits.billing.SubscriptionRepository
import com.twobits.pricedrop.data.provider.AiFeature
import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderMode
import com.twobits.pricedrop.data.provider.ProviderSettingsStore
import com.twobits.pricedrop.data.remote.dto.BarcodeResponseDto
import com.twobits.pricedrop.data.remote.dto.ChatResponseDto
import com.twobits.pricedrop.data.remote.dto.CouponsResponseDto
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
    ) {
        private val jsonMedia = "application/json; charset=utf-8".toMediaType()

        // ---------------------------------------------------------------------------
        // Public API
        // ---------------------------------------------------------------------------

        suspend fun search(
            query: String,
            maxResults: Int = 10,
        ): SearchResponseDto {
            if (isByok(PriceDropProvider.SHOPPING)) {
                return searchDirect(query, maxResults, byokKey(PriceDropProvider.SHOPPING))
            }
            if (isByok(PriceDropProvider.SERPER)) {
                return searchDirectSerper(query, maxResults, byokKey(PriceDropProvider.SERPER))
            }
            if (isByok(PriceDropProvider.WEB_SEARCH)) {
                return searchDirectJina(query, maxResults, byokKey(PriceDropProvider.WEB_SEARCH))
            }
            val body =
                JsonObject().apply {
                    addProperty("query", query)
                    addProperty("maxResults", maxResults)
                }
            return workerPost("/v1/pricedrop/search", body, SearchResponseDto::class.java)
        }

        suspend fun price(
            asin: String? = null,
            upc: String? = null,
        ): PriceResponseDto {
            // Price and barcode lookups always use Rainforest API (Amazon product data).
            // SearchAPI.io (SHOPPING) is for search only; it has no equivalent price endpoint.
            if (isByok(PriceDropProvider.RAINFOREST)) {
                return priceDirect(asin, upc, byokKey(PriceDropProvider.RAINFOREST))
            }
            val body =
                JsonObject().apply {
                    asin?.takeIf { it.isNotBlank() }?.let { addProperty("asin", it) }
                    upc?.takeIf { it.isNotBlank() }?.let { addProperty("upc", it) }
                }
            return workerPost("/v1/pricedrop/price", body, PriceResponseDto::class.java)
        }

        suspend fun history(asin: String): HistoryResponseDto {
            // Rainforest backs history in both modes now (Keepa removed) — same provider
            // priceDirect()/priceDrop() already use, so BYOK history needs no separate key.
            if (isByok(PriceDropProvider.RAINFOREST)) {
                return historyDirect(asin, byokKey(PriceDropProvider.RAINFOREST))
            }
            val body = JsonObject().apply { addProperty("asin", asin) }
            return workerPost("/v1/pricedrop/history", body, HistoryResponseDto::class.java)
        }

        suspend fun coupons(
            query: String,
            domain: String? = null,
        ): CouponsResponseDto {
            if (isByok(PriceDropProvider.COUPON)) {
                return couponsDirect(query, domain, byokKey(PriceDropProvider.COUPON))
            }
            val body =
                JsonObject().apply {
                    addProperty("query", query)
                    domain?.takeIf { it.isNotBlank() }?.let { addProperty("domain", it) }
                }
            return workerPost("/v1/pricedrop/coupons", body, CouponsResponseDto::class.java)
        }

        suspend fun barcode(upc: String): BarcodeResponseDto {
            if (isByok(PriceDropProvider.RAINFOREST)) {
                return barcodeDirect(upc, byokKey(PriceDropProvider.RAINFOREST))
            }
            val body = JsonObject().apply { addProperty("upc", upc) }
            return workerPost("/v1/pricedrop/barcode", body, BarcodeResponseDto::class.java)
        }

        /**
         * Shopping-scoped chat. Pro routes through the Worker's OpenAI proxy; BYOK calls
         * api.openai.com directly. [history] is the full conversation (all user + assistant turns)
         * including the new user message as the last entry; the system prompt is prepended here.
         * Model is resolved from the user's AI Config selection for [AiFeature.ASK].
         */
        suspend fun chat(
            systemPrompt: String,
            history: List<com.twobits.pricedrop.ui.ask.ChatMessage>,
        ): String {
            val isProMode = !isByok(PriceDropProvider.OPENAI)
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
            val groundingContext =
                if (isByok(PriceDropProvider.WEB_SEARCH)) {
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
            val dto = post("$baseUrl/v1/chat/completions", body, ChatResponseDto::class.java, authHeader, op = "chat")
            return dto.choices
                .firstOrNull()
                ?.message
                ?.content
                .orEmpty()
        }

        // ---------------------------------------------------------------------------
        // BYOK — direct provider calls
        // ---------------------------------------------------------------------------

        private suspend fun searchDirect(
            query: String,
            maxResults: Int,
            apiKey: String,
        ): SearchResponseDto =
            withContext(Dispatchers.IO) {
                val url =
                    "https://www.searchapi.io/api/v1/search"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("engine", "google_shopping")
                        .addQueryParameter("q", query)
                        .addQueryParameter("api_key", apiKey)
                        .addQueryParameter("num", maxResults.toString())
                        .build()
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IOException(friendlyError(response.code, text))
                    val data = gson.fromJson(text, JsonObject::class.java)
                    // SearchAPI.io Google Shopping spreads results across shopping_results
                    // and popular_products, and uses seller / product_link (not source / link).
                    val items =
                        listOfNotNull(
                            data.getAsJsonArray("shopping_results"),
                            data.getAsJsonArray("popular_products"),
                        ).flatten()
                    val results =
                        items.take(maxResults).map { item ->
                            val r = item.asJsonObject

                            fun field(vararg keys: String): String? = keys.firstNotNullOfOrNull { k -> r[k]?.takeIf { it.isJsonPrimitive }?.asString }
                            SearchResultDto(
                                title = field("title"),
                                price = field("price"),
                                source = field("seller", "source"),
                                url = field("product_link", "link"),
                            )
                        }
                    SearchResponseDto(results)
                }
            }

        // Serper's dedicated /shopping endpoint (not /search) — mirrors SearchAPI's
        // google_shopping engine so Serper is a real substitute (structured price data),
        // not a repeat of the Jina fallback's price = null gap.
        private suspend fun searchDirectSerper(
            query: String,
            maxResults: Int,
            apiKey: String,
        ): SearchResponseDto =
            withContext(Dispatchers.IO) {
                val requestBody =
                    JsonObject()
                        .apply { addProperty("q", query) }
                        .toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request =
                    Request
                        .Builder()
                        .url("https://google.serper.dev/shopping")
                        .addHeader("X-API-KEY", apiKey.trim())
                        .post(requestBody)
                        .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IOException(friendlyError(response.code, text))
                    val data = gson.fromJson(text, JsonObject::class.java)
                    val items = data.getAsJsonArray("shopping") ?: JsonArray()
                    val results =
                        items.take(maxResults).map { item ->
                            val r = item.asJsonObject

                            fun field(vararg keys: String): String? = keys.firstNotNullOfOrNull { k -> r[k]?.takeIf { it.isJsonPrimitive }?.asString }
                            SearchResultDto(
                                title = field("title"),
                                price = field("price"),
                                source = field("source"),
                                url = field("link"),
                            )
                        }
                    SearchResponseDto(results)
                }
            }

        private suspend fun priceDirect(
            asin: String?,
            upc: String?,
            apiKey: String,
        ): PriceResponseDto =
            withContext(Dispatchers.IO) {
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
            }

        // Mirrors the worker's pdHistory: Rainforest already provides Amazon price history
        // alongside product data, eliminating the need for a separate Keepa subscription.
        private suspend fun historyDirect(
            asin: String,
            apiKey: String,
        ): HistoryResponseDto =
            withContext(Dispatchers.IO) {
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
            }

        // Rainforest's price_history dates have been observed as ISO-8601 ("2024-01-15" or
        // full instants); try both and skip entries that don't parse rather than guessing.
        private fun parseRainforestDate(raw: String): Long? =
            runCatching { java.time.Instant.parse(raw).toEpochMilli() }
                .recoverCatching { java.time.LocalDate.parse(raw).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }
                .getOrNull()

        private suspend fun couponsDirect(
            query: String,
            domain: String?,
            apiKey: String,
        ): CouponsResponseDto =
            withContext(Dispatchers.IO) {
                val urlBuilder =
                    "https://feed.linkmydeals.com/getOffers/"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("API_KEY", apiKey)
                        .addQueryParameter("format", "json")
                        .addQueryParameter("off_record", "1")

                val request =
                    Request
                        .Builder()
                        .url(urlBuilder.build())
                        .get()
                        .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw IOException(friendlyError(response.code, text))
                    val data = gson.fromJson(text, JsonObject::class.java)
                    runCatching { LinkMyDealsCouponMapper.map(data, query, domain) }
                        .getOrElse { throw IOException(it.message ?: "LinkMyDeals error", it) }
                }
            }

        private suspend fun barcodeDirect(
            upc: String,
            apiKey: String,
        ): BarcodeResponseDto =
            withContext(Dispatchers.IO) {
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
            }

        /**
         * Reads a product page via Jina Reader when [PriceDropProvider.WEB_SEARCH] is in BYOK mode.
         * Returns empty string in Pro mode (the Worker handles page reading server-side) or on error.
         */
        suspend fun readPage(url: String): String {
            if (!isByok(PriceDropProvider.WEB_SEARCH)) return ""
            return readPageDirect(url, providerSettings.getKey(PriceDropProvider.WEB_SEARCH))
        }

        private suspend fun readPageDirect(
            url: String,
            apiKey: String,
        ): String =
            withContext(Dispatchers.IO) {
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
                }.getOrDefault("")
            }

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
            return runCatching {
                val body =
                    JsonObject().apply {
                        addProperty("model", if (isProMode) PRO_CHAT_MODEL else BYOK_CHAT_MODEL)
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
                    post("$baseUrl/v1/chat/completions", body, ChatResponseDto::class.java, authHeader, op = "extract-product")
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
        ): T = post("$PRO_BASE_URL$path", body, type, "Bearer ${subscriptionRepository.getAppUserId()}")

        private suspend fun <T> post(
            url: String,
            body: JsonObject,
            type: Class<T>,
            authHeader: String,
            op: String? = null,
        ): T =
            withContext(Dispatchers.IO) {
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
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException(friendlyError(response.code, text))
                    }
                    gson.fromJson(text, type)
                }
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
