package com.twobits.pricedrop.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.twobits.billing.SubscriptionRepository
import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderMode
import com.twobits.pricedrop.data.provider.ProviderSettingsStore
import com.twobits.pricedrop.data.remote.dto.BarcodeResponseDto
import com.twobits.pricedrop.data.remote.dto.ChatResponseDto
import com.twobits.pricedrop.data.remote.dto.CouponsResponseDto
import com.twobits.pricedrop.data.remote.dto.HistoryResponseDto
import com.twobits.pricedrop.data.remote.dto.PriceResponseDto
import com.twobits.pricedrop.data.remote.dto.SearchResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls the TwoBits Worker PriceDrop endpoints. In Pro mode all requests route through
 * [PRO_BASE_URL] using the RevenueCat app-user ID as a bearer token. In BYOK mode the
 * OPENAI provider routes directly to api.openai.com; other providers fall back to Pro
 * until per-provider request/response adapters are added.
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

        /** Returns (baseUrl, Authorization header value) for the given provider. */
        private suspend fun resolveConfig(provider: PriceDropProvider): Pair<String, String> {
            val mode = providerSettings.getMode(provider)
            return if (mode == ProviderMode.BYOK) {
                provider.byokBaseUrl.trimEnd('/') to "Bearer ${providerSettings.getKey(provider)}"
            } else {
                PRO_BASE_URL to "Bearer ${subscriptionRepository.getAppUserId()}"
            }
        }

        suspend fun search(
            query: String,
            maxResults: Int = 10,
        ): SearchResponseDto {
            val (base, auth) = resolveConfig(PriceDropProvider.SHOPPING)
            val body =
                JsonObject().apply {
                    addProperty("query", query)
                    addProperty("maxResults", maxResults)
                }
            return post("/v1/pricedrop/search", body, SearchResponseDto::class.java, base, auth)
        }

        suspend fun price(
            asin: String? = null,
            upc: String? = null,
        ): PriceResponseDto {
            val (base, auth) = resolveConfig(PriceDropProvider.SHOPPING)
            val body =
                JsonObject().apply {
                    asin?.takeIf { it.isNotBlank() }?.let { addProperty("asin", it) }
                    upc?.takeIf { it.isNotBlank() }?.let { addProperty("upc", it) }
                }
            return post("/v1/pricedrop/price", body, PriceResponseDto::class.java, base, auth)
        }

        suspend fun history(asin: String): HistoryResponseDto {
            val (base, auth) = resolveConfig(PriceDropProvider.KEEPA)
            val body = JsonObject().apply { addProperty("asin", asin) }
            return post("/v1/pricedrop/history", body, HistoryResponseDto::class.java, base, auth)
        }

        suspend fun coupons(
            query: String,
            domain: String? = null,
        ): CouponsResponseDto {
            val (base, auth) = resolveConfig(PriceDropProvider.COUPON)
            val body =
                JsonObject().apply {
                    addProperty("query", query)
                    domain?.takeIf { it.isNotBlank() }?.let { addProperty("domain", it) }
                }
            return post("/v1/pricedrop/coupons", body, CouponsResponseDto::class.java, base, auth)
        }

        suspend fun barcode(upc: String): BarcodeResponseDto {
            val (base, auth) = resolveConfig(PriceDropProvider.SHOPPING)
            val body = JsonObject().apply { addProperty("upc", upc) }
            return post("/v1/pricedrop/barcode", body, BarcodeResponseDto::class.java, base, auth)
        }

        /**
         * Shopping-scoped chat. Pro routes through the Worker's OpenAI proxy; BYOK calls
         * api.openai.com directly. [history] is the full conversation (all user + assistant turns)
         * including the new user message as the last entry; the system prompt is prepended here.
         */
        suspend fun chat(
            systemPrompt: String,
            history: List<com.twobits.pricedrop.ui.ask.ChatMessage>,
        ): String {
            val (base, auth) = resolveConfig(PriceDropProvider.OPENAI)
            val model = if (base == PRO_BASE_URL) PRO_CHAT_MODEL else BYOK_CHAT_MODEL
            val messages =
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("role", "system")
                            addProperty("content", systemPrompt)
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
            val dto = post("/v1/chat/completions", body, ChatResponseDto::class.java, base, auth)
            return dto.choices
                .firstOrNull()
                ?.message
                ?.content
                .orEmpty()
        }

        private suspend fun <T> post(
            path: String,
            body: JsonObject,
            type: Class<T>,
            baseUrl: String,
            authHeader: String,
        ): T =
            withContext(Dispatchers.IO) {
                val request =
                    Request
                        .Builder()
                        .url("$baseUrl$path")
                        .addHeader("Authorization", authHeader)
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
        }
    }
