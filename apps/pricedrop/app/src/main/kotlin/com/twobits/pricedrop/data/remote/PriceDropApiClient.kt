package com.twobits.pricedrop.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.twobits.billing.SubscriptionRepository
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
 * Calls the TwoBits Worker PriceDrop endpoints (Pro passthrough). Auth is the RevenueCat
 * app user ID as a bearer token; the Worker validates the `pricedrop_pro` entitlement and
 * holds all provider secrets.
 *
 * BYOK-direct provider calls (SerpAPI/Rainforest/Keepa/CouponLayer with the user's own keys)
 * are added in the provider-settings track, where keys are entered.
 */
@Singleton
class PriceDropApiClient
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val gson: Gson,
        private val subscriptionRepository: SubscriptionRepository,
    ) {
        private val jsonMedia = "application/json; charset=utf-8".toMediaType()
        private val baseUrl = "https://api.twobits.app"

        suspend fun search(
            query: String,
            maxResults: Int = 10,
        ): SearchResponseDto {
            val body =
                JsonObject().apply {
                    addProperty("query", query)
                    addProperty("maxResults", maxResults)
                }
            return post("/v1/pricedrop/search", body, SearchResponseDto::class.java)
        }

        suspend fun price(
            asin: String? = null,
            upc: String? = null,
        ): PriceResponseDto {
            val body =
                JsonObject().apply {
                    asin?.takeIf { it.isNotBlank() }?.let { addProperty("asin", it) }
                    upc?.takeIf { it.isNotBlank() }?.let { addProperty("upc", it) }
                }
            return post("/v1/pricedrop/price", body, PriceResponseDto::class.java)
        }

        suspend fun history(asin: String): HistoryResponseDto {
            val body = JsonObject().apply { addProperty("asin", asin) }
            return post("/v1/pricedrop/history", body, HistoryResponseDto::class.java)
        }

        suspend fun coupons(
            query: String,
            domain: String? = null,
        ): CouponsResponseDto {
            val body =
                JsonObject().apply {
                    addProperty("query", query)
                    domain?.takeIf { it.isNotBlank() }?.let { addProperty("domain", it) }
                }
            return post("/v1/pricedrop/coupons", body, CouponsResponseDto::class.java)
        }

        suspend fun barcode(upc: String): BarcodeResponseDto {
            val body = JsonObject().apply { addProperty("upc", upc) }
            return post("/v1/pricedrop/barcode", body, BarcodeResponseDto::class.java)
        }

        /** Shopping-scoped chat via the Worker's OpenAI proxy. Returns the assistant text. */
        suspend fun chat(
            systemPrompt: String,
            userMessage: String,
            model: String = "gpt-5-mini",
        ): String {
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
                            addProperty("content", userMessage)
                        },
                    )
                }
            val body =
                JsonObject().apply {
                    addProperty("model", model)
                    add("messages", messages)
                }
            val dto = post("/v1/chat/completions", body, ChatResponseDto::class.java)
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
        ): T =
            withContext(Dispatchers.IO) {
                val appUserId = subscriptionRepository.getAppUserId()
                val request =
                    Request
                        .Builder()
                        .url("$baseUrl$path")
                        .addHeader("Authorization", "Bearer $appUserId")
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
    }
