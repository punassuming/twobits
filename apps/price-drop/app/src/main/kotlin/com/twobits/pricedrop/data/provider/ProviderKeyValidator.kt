package com.twobits.pricedrop.data.provider

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes a live network call to verify that a BYOK API key is accepted by the provider,
 * not just syntactically valid. Each provider has a low-cost or free "account/ping" endpoint
 * that returns 401/403/error-JSON for bad keys without consuming meaningful quota.
 */
@Singleton
class ProviderKeyValidator
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val gson: Gson,
    ) {
        suspend fun validate(
            provider: PriceDropProvider,
            key: String,
        ): Result<String> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val trimmed = key.trim()
                    require(trimmed.isNotBlank()) { "Enter an API key first" }
                    when (provider) {
                        PriceDropProvider.OPENAI -> testOpenAi(trimmed)
                        PriceDropProvider.WEB_SEARCH -> testJina(trimmed)
                        PriceDropProvider.SHOPPING -> testSerpApi(trimmed)
                        PriceDropProvider.KEEPA -> testKeepa(trimmed)
                        PriceDropProvider.COUPON -> testCouponlayer(trimmed)
                        PriceDropProvider.RAINFOREST -> testRainforest(trimmed)
                    }
                }
            }

        private fun testOpenAi(key: String): String {
            val request =
                Request
                    .Builder()
                    .url("https://api.openai.com/v1/models")
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .get()
                    .build()
            okHttpClient.newCall(request).execute().use { response ->
                return when {
                    response.isSuccessful -> "Connected to OpenAI"
                    response.code == 401 -> throw IllegalStateException("OpenAI rejected this API key")
                    response.code == 429 -> throw IllegalStateException("Rate-limited — try again shortly")
                    else -> throw IllegalStateException("OpenAI returned HTTP ${response.code}")
                }
            }
        }

        private fun testJina(key: String): String {
            val url =
                "https://s.jina.ai/"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("q", "test")
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .header("X-Return-Format", "json")
                    .get()
                    .build()
            okHttpClient.newCall(request).execute().use { response ->
                return when {
                    response.isSuccessful -> "Connected to Jina AI"
                    response.code == 401 || response.code == 422 ->
                        throw IllegalStateException("Jina AI rejected this key")
                    response.code == 402 ->
                        throw IllegalStateException("Jina AI account has no remaining credits")
                    else -> throw IllegalStateException("Jina AI returned HTTP ${response.code}")
                }
            }
        }

        private fun testSerpApi(key: String): String {
            val url =
                "https://serpapi.com/account"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("api_key", key)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            okHttpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                return when {
                    response.isSuccessful -> {
                        val plan =
                            runCatching {
                                gson
                                    .fromJson(text, JsonObject::class.java)
                                    ?.get("plan_name")
                                    ?.asString
                            }.getOrNull()
                        if (plan != null) "Connected to SerpAPI — $plan plan" else "Connected to SerpAPI"
                    }
                    response.code == 401 || response.code == 403 ->
                        throw IllegalStateException("SerpAPI rejected this key")
                    else -> throw IllegalStateException("SerpAPI returned HTTP ${response.code}")
                }
            }
        }

        private fun testKeepa(key: String): String {
            val url =
                "https://api.keepa.com/token"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("key", key)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            okHttpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Keepa rejected this key (HTTP ${response.code})")
                }
                val json = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
                val tokensLeft = json?.get("tokensLeft")?.asLong
                return if (tokensLeft != null) {
                    "Connected to Keepa — $tokensLeft tokens remaining"
                } else {
                    "Connected to Keepa"
                }
            }
        }

        private fun testCouponlayer(key: String): String {
            val url =
                "https://api.couponlayer.com/coupons"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("access_key", key)
                    .addQueryParameter("retailer", "amazon.com")
                    .addQueryParameter("search", "test")
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            okHttpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Couponlayer returned HTTP ${response.code}")
                }
                val json = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
                if (json?.get("success")?.asBoolean != true) {
                    val code =
                        json
                            ?.get("error")
                            ?.asJsonObject
                            ?.get("code")
                            ?.asInt
                    val info =
                        json
                            ?.get("error")
                            ?.asJsonObject
                            ?.get("info")
                            ?.asString
                    throw IllegalStateException(
                        when (code) {
                            101 -> "Couponlayer rejected this key — invalid access key"
                            102 -> "Couponlayer key is inactive or has no remaining requests"
                            else -> info ?: "Couponlayer validation failed"
                        },
                    )
                }
                return "Connected to Couponlayer"
            }
        }

        private fun testRainforest(key: String): String {
            val url =
                "https://api.rainforestapi.com/account"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("api_key", key)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            okHttpClient.newCall(request).execute().use { response ->
                return when {
                    response.isSuccessful -> "Connected to Rainforest API"
                    response.code == 401 || response.code == 403 ->
                        throw IllegalStateException("Rainforest API rejected this key")
                    else -> throw IllegalStateException("Rainforest API returned HTTP ${response.code}")
                }
            }
        }
    }
