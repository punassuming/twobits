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
import com.twobits.pricedrop.data.remote.dto.CouponDto
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
                return searchDirect(query, maxResults, providerSettings.getKey(PriceDropProvider.SHOPPING))
            }
            if (isByok(PriceDropProvider.WEB_SEARCH)) {
                return searchDirectJina(query, maxResults, providerSettings.getKey(PriceDropProvider.WEB_SEARCH))
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
            // SerpAPI (SHOPPING) is for search only; it has no equivalent price endpoint.
            if (isByok(PriceDropProvider.RAINFOREST)) {
                return priceDirect(asin, upc, providerSettings.getKey(PriceDropProvider.RAINFOREST))
            }
            val body =
                JsonObject().apply {
                    asin?.takeIf { it.isNotBlank() }?.let { addProperty("asin", it) }
                    upc?.takeIf { it.isNotBlank() }?.let { addProperty("upc", it) }
                }
            return workerPost("/v1/pricedrop/price", body, PriceResponseDto::class.java)
        }

        suspend fun history(asin: String): HistoryResponseDto {
            if (isByok(PriceDropProvider.KEEPA)) {
                return historyDirect(asin, providerSettings.getKey(PriceDropProvider.KEEPA))
            }
            val body = JsonObject().apply { addProperty("asin", asin) }
            return workerPost("/v1/pricedrop/history", body, HistoryResponseDto::class.java)
        }

        suspend fun coupons(
            query: String,
            domain: String? = null,
        ): CouponsResponseDto {
            if (isByok(PriceDropProvider.COUPON)) {
                return couponsDirect(query, domain, providerSettings.getKey(PriceDropProvider.COUPON))
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
                return barcodeDirect(upc, providerSettings.getKey(PriceDropProvider.RAINFOREST))
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
                    "Bearer ${providerSettings.getKey(PriceDropProvider.OPENAI)}"
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
            val dto = post("$baseUrl/v1/chat/completions", body, ChatResponseDto::class.java, authHeader)
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
                    "https://serpapi.com/search"
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
                    val results =
                        (data.getAsJsonArray("shopping_results") ?: JsonArray())
                            .take(maxResults)
                            .map { item ->
                                val r = item.asJsonObject
                                SearchResultDto(
                                    title = r["title"]?.asString,
                                    price = r["price"]?.asString,
                                    source = r["source"]?.asString,
                                    url = r["link"]?.asString,
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

        private suspend fun historyDirect(
            asin: String,
            apiKey: String,
        ): HistoryResponseDto =
            withContext(Dispatchers.IO) {
                val url =
                    "https://api.keepa.com/product"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("key", apiKey)
                        .addQueryParameter("domain", "1")
                        .addQueryParameter("asin", asin)
                        .addQueryParameter("history", "1")
                        .addQueryParameter("stats", "180")
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
                        data["products"]?.asJsonArray?.firstOrNull()?.asJsonObject
                            ?: return@use HistoryResponseDto()

                    // CSV index 0 = Amazon price (in cents / 100); -1 = unavailable.
                    val csv = product["csv"]?.asJsonArray?.firstOrNull()?.asJsonArray
                    val history = mutableListOf<HistoryPointDto>()
                    csv?.let { arr ->
                        var i = 0
                        while (i + 1 < arr.size()) {
                            val priceRaw = runCatching { arr[i + 1].asInt }.getOrNull() ?: -1
                            if (priceRaw != -1) {
                                val keepaMin = runCatching { arr[i].asLong }.getOrNull() ?: 0L
                                history.add(HistoryPointDto(ts = KEEPA_EPOCH_MS + keepaMin * 60_000L, price = priceRaw / 100.0))
                            }
                            i += 2
                        }
                    }
                    val lowestPrice =
                        product["stats"]
                            ?.asJsonObject
                            ?.get("min")
                            ?.asJsonArray
                            ?.firstOrNull()
                            ?.let { runCatching { it.asInt }.getOrNull() }
                            ?.takeIf { it != -1 }
                            ?.let { it / 100.0 }
                    HistoryResponseDto(history = history, lowestPrice = lowestPrice)
                }
            }

        private suspend fun couponsDirect(
            query: String,
            domain: String?,
            apiKey: String,
        ): CouponsResponseDto =
            withContext(Dispatchers.IO) {
                val urlBuilder =
                    "https://api.couponlayer.com/coupons"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("access_key", apiKey)
                        .addQueryParameter("search", query)
                domain?.takeIf { it.isNotBlank() }?.let { urlBuilder.addQueryParameter("category", it) }

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
                    if (data["success"]?.asBoolean != true) {
                        throw IOException(data["error"]?.asJsonObject?.get("info")?.asString ?: "CouponLayer error")
                    }
                    val coupons =
                        (data["data"]?.asJsonArray ?: JsonArray())
                            .take(10)
                            .map { item ->
                                val c = item.asJsonObject
                                CouponDto(
                                    code = c["coupon_code"]?.asString,
                                    description = c["coupon_description"]?.asString,
                                    discount = c["coupon_discount"]?.asString,
                                    type = c["coupon_type"]?.asString,
                                    expires = c["coupon_expiry_date"]?.asString,
                                    store = c["merchant_name"]?.asString,
                                )
                            }
                    CouponsResponseDto(coupons)
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
                    "Bearer ${providerSettings.getKey(PriceDropProvider.OPENAI)}"
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
                    post("$baseUrl/v1/chat/completions", body, ChatResponseDto::class.java, authHeader)
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
        ): T =
            withContext(Dispatchers.IO) {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .addHeader("Authorization", authHeader)
                        .addHeader("X-TwoBits-App", "pricedrop")
                        .addHeader("Content-Type", "application/json")
                        .post(gson.toJson(body).toRequestBody(jsonMedia))
                        .build()
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
            private const val KEEPA_EPOCH_MS = 1_293_840_000_000L // 2011-01-01 00:00 UTC
        }
    }
