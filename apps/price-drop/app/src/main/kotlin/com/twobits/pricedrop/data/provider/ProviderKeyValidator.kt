package com.twobits.pricedrop.data.provider

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
                        PriceDropProvider.SHOPPING -> testSearchApi(trimmed)
                        PriceDropProvider.SERPER -> testSerper(trimmed)
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

        private fun testSearchApi(key: String): String {
            // SearchAPI.io has no free account endpoint, so verify with a minimal
            // 1-result search (consumes one search credit per Test).
            val url =
                "https://www.searchapi.io/api/v1/search"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("engine", "google")
                    .addQueryParameter("q", "ping")
                    .addQueryParameter("num", "1")
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
                    response.isSuccessful -> "Connected to SearchAPI.io"
                    response.code == 401 || response.code == 403 ->
                        throw IllegalStateException("SearchAPI.io rejected this key")
                    else -> throw IllegalStateException("SearchAPI.io returned HTTP ${response.code}")
                }
            }
        }

        private fun testSerper(key: String): String {
            // Serper has no free account-ping endpoint, so verify with a minimal
            // 1-result search (consumes one search credit per Test).
            val body =
                JsonObject()
                    .apply {
                        addProperty("q", "ping")
                        addProperty("num", 1)
                    }.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request =
                Request
                    .Builder()
                    .url("https://google.serper.dev/search")
                    .header("X-API-KEY", key)
                    .post(body)
                    .build()
            okHttpClient.newCall(request).execute().use { response ->
                return when {
                    response.isSuccessful -> "Connected to Serper.dev"
                    response.code == 401 || response.code == 403 ->
                        throw IllegalStateException("Serper.dev rejected this key")
                    else -> throw IllegalStateException("Serper.dev returned HTTP ${response.code}")
                }
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
