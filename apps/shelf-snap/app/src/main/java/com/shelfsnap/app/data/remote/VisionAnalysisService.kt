package com.shelfsnap.app.data.remote

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.util.ApiKeyValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyses photos of a donation item via OpenAI vision models and returns structured
 * metadata. GPT-5-family models are routed through the Responses API; older models
 * use Chat Completions.
 *
 * The API key is supplied at call-time so the user can update it at any point via Settings.
 */
@Singleton
class VisionAnalysisService
    @Inject
    constructor() {
        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

        private val gson = Gson()
        private val json = "application/json; charset=utf-8".toMediaType()

        /**
         * Sends a lightweight GET /v1/models request to verify that [apiKey] is accepted by
         * OpenAI. Returns [Result.success] or [Result.failure] with a user-facing message.
         */
        suspend fun testKey(apiKey: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                if (!ApiKeyValidator.isValid(apiKey)) {
                    return@withContext Result.failure(IOException(ERROR_INVALID_KEY))
                }
                runCatching {
                    val request =
                        Request
                            .Builder()
                            .url("https://api.openai.com/v1/models")
                            .addHeader("Authorization", "Bearer $apiKey")
                            .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException(friendlyHttpError(response.code))
                        }
                    }
                }.recoverCatching { e ->
                    val friendly = if (e is IOException && e.message != null) e.message!! else friendlyNetworkError(e)
                    throw IOException(friendly)
                }
            }

        /**
         * Analyses [photoPaths] and returns a [DraftItemResult].
         * Returns a result with [DraftItemResult.error] set if the call fails.
         *
         * [baseUrl] and [authHeader] can be overridden to route through the TwoBits
         * Worker proxy in Pro mode instead of calling OpenAI directly.
         */
        suspend fun analyse(
            photoPaths: List<String>,
            apiKey: String,
            model: String,
            baseUrl: String = "https://api.openai.com",
            authHeader: String = "Bearer $apiKey",
        ): DraftItemResult =
            withContext(Dispatchers.IO) {
                // Fail fast on a missing/obviously-invalid key — no network round-trip needed.
                // Skip validation when routing through the Worker (the key is a RevenueCat user ID).
                if (baseUrl == "https://api.openai.com" && !ApiKeyValidator.isValid(apiKey)) {
                    Log.w(TAG, "Analysis aborted: API key missing or invalid format")
                    return@withContext errorResult(ERROR_INVALID_KEY)
                }
                runCatching {
                    val isResponsesApi = isResponsesModel(model)
                    val requestBody =
                        if (isResponsesApi) {
                            buildResponsesBody(photoPaths, model)
                        } else {
                            buildChatBody(photoPaths, model)
                        }
                    val endpoint = if (isResponsesApi) "v1/responses" else "v1/chat/completions"
                    val request =
                        Request
                            .Builder()
                            .url("$baseUrl/$endpoint")
                            .addHeader("Authorization", authHeader)
                            .addHeader("X-TwoBits-App", "shelfsnap")
                            .addHeader("X-TwoBits-Op", "vision")
                            .addHeader("Content-Type", "application/json")
                            .post(gson.toJson(requestBody).toRequestBody(json))
                            .build()

                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            // Log the status (never the key or request body) to aid field debugging.
                            Log.w(TAG, "OpenAI request failed: HTTP ${response.code}")
                            return@use errorResult(friendlyHttpError(response.code))
                        }
                        parseResponse(body, isResponsesApi)
                    }
                }.getOrElse { e ->
                    Log.w(TAG, "OpenAI request threw ${e.javaClass.simpleName}")
                    errorResult(friendlyNetworkError(e))
                }
            }

        private fun isResponsesModel(model: String): Boolean = model.startsWith("gpt-5")

        private fun buildChatBody(
            photoPaths: List<String>,
            model: String,
        ): JsonObject {
            val userContent =
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", USER_PROMPT)
                        },
                    )
                    photoPaths.forEach { path ->
                        add(
                            JsonObject().apply {
                                addProperty("type", "image_url")
                                add(
                                    "image_url",
                                    JsonObject().apply {
                                        addProperty("url", "data:image/jpeg;base64,${encodeImageToBase64(path)}")
                                        addProperty("detail", "auto")
                                    },
                                )
                            },
                        )
                    }
                }
            return JsonObject().apply {
                addProperty("model", model)
                add("messages", buildMessages(userContent))
                addProperty("max_tokens", 800)
            }
        }

        private fun buildResponsesBody(
            photoPaths: List<String>,
            model: String,
        ): JsonObject {
            // Responses API content parts: image_url is a plain string and detail is a sibling
            // property — a different shape from Chat Completions' nested image_url object.
            val userContent =
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("type", "input_text")
                            addProperty("text", USER_PROMPT)
                        },
                    )
                    photoPaths.forEach { path ->
                        add(
                            JsonObject().apply {
                                addProperty("type", "input_image")
                                addProperty("image_url", "data:image/jpeg;base64,${encodeImageToBase64(path)}")
                                addProperty("detail", "auto")
                            },
                        )
                    }
                }
            return JsonObject().apply {
                addProperty("model", model)
                add("input", buildMessages(userContent))
                // Reasoning tokens count against max_output_tokens; leave headroom for the JSON.
                addProperty("max_output_tokens", 1200)
                add("reasoning", JsonObject().apply { addProperty("effort", "low") })
            }
        }

        private fun buildMessages(userContent: JsonArray): JsonArray =
            JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", SYSTEM_PROMPT)
                    },
                )
                add(
                    JsonObject().apply {
                        addProperty("role", "user")
                        add("content", userContent)
                    },
                )
            }

        private fun parseResponse(
            responseJson: String,
            isResponsesApi: Boolean,
        ): DraftItemResult =
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
                    }

                // Strip possible markdown code fences
                val cleaned =
                    content
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                val obj = JsonParser.parseString(cleaned).asJsonObject
                DraftItemResult(
                    category = obj.get("category")?.asString ?: "Other",
                    brand = obj.get("brand")?.asString ?: "",
                    model = obj.get("model")?.asString ?: "",
                    description = obj.get("description")?.asString ?: "",
                    tags = obj.getAsJsonArray("tags")?.map { it.asString } ?: emptyList(),
                    condition =
                        runCatching {
                            Condition.valueOf(obj.get("condition")?.asString ?: "GOOD")
                        }.getOrDefault(Condition.GOOD),
                    estimatedValue = obj.get("estimatedValue")?.asDouble ?: 0.0,
                    confidencePercent = obj.get("confidencePercent")?.asInt ?: 0,
                )
            }.getOrElse { e ->
                Log.w(TAG, "Failed to parse OpenAI response: ${e.javaClass.simpleName}")
                errorResult(ERROR_PARSE)
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

        /**
         * Scales down and base64-encodes a JPEG image file to keep request size reasonable.
         */
        private fun encodeImageToBase64(path: String): String {
            val file = File(path)
            val options =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(path, options)
            val maxDim = 512
            val scale =
                maxOf(
                    options.outWidth / maxDim,
                    options.outHeight / maxDim,
                    1,
                )
            val scaled =
                BitmapFactory.Options().apply {
                    inSampleSize = scale
                }
            val bitmap = BitmapFactory.decodeFile(path, scaled)
            val out = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            bitmap.recycle()
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }

        private fun errorResult(message: String) =
            DraftItemResult(
                category = "",
                brand = "",
                model = "",
                description = "",
                tags = emptyList(),
                condition = Condition.GOOD,
                estimatedValue = 0.0,
                confidencePercent = 0,
                error = message,
            )

        companion object {
            private const val TAG = "VisionAnalysisService"

            private val SYSTEM_PROMPT =
                """
                You are an expert at evaluating household goods for charitable donation.
                Given one or more photos of a single item, respond ONLY with valid JSON in this exact schema:
                {
                  "category": "<short category, e.g. Clothing, Electronics, Books, Furniture, Toys, Kitchenware, Other>",
                  "brand": "<brand or manufacturer name, or empty string if unknown>",
                  "model": "<model name or number, or empty string if unknown>",
                  "description": "<3–5 sentence marketplace listing description. Cover: overall condition and appearance, notable features or design elements, any visible defects or wear, material/fabric/finish if discernible, and how the item is best used. Write as if posting on eBay — specific, factual, no fluff.>",
                  "tags": ["<6–10 searchable marketplace keywords — include brand name (if present), style/era, material, dominant color, use case, and condition descriptor. All lowercase.>"],
                  "condition": "<one of: EXCELLENT, GOOD, FAIR, POOR>",
                  "estimatedValue": <number, USD resale/donation estimate>,
                  "confidencePercent": <integer 0-100>
                }
                Do not include any explanation outside the JSON object.
                """.trimIndent()

            private const val USER_PROMPT =
                "Please analyse the item in the following photo(s) and return the JSON."

            internal const val ERROR_INVALID_KEY =
                "Invalid or missing OpenAI API key. Check Settings."
            internal const val ERROR_RATE_LIMITED =
                "Rate limited by OpenAI. Wait a moment and try again."
            internal const val ERROR_UNAVAILABLE =
                "OpenAI is temporarily unavailable. Try again shortly."
            internal const val ERROR_TIMEOUT =
                "Analysis timed out. Check your connection and try again."
            internal const val ERROR_NETWORK =
                "Network error. Check your connection and try again."
            internal const val ERROR_PARSE =
                "Couldn't read the AI response. Please try again."
            internal const val ERROR_UNKNOWN = "Something went wrong. Please try again."

            /** Maps an HTTP status code to a user-facing message. */
            internal fun friendlyHttpError(code: Int): String =
                when {
                    code == 401 || code == 403 -> ERROR_INVALID_KEY
                    code == 404 -> ERROR_MODEL_NOT_FOUND
                    code == 429 -> ERROR_RATE_LIMITED
                    code in 500..599 -> ERROR_UNAVAILABLE
                    else -> ERROR_UNAVAILABLE
                }

            internal const val ERROR_MODEL_NOT_FOUND =
                "Selected model isn't available. Try a different model in Settings → AI."

            /** Maps a thrown exception to a user-facing message. */
            internal fun friendlyNetworkError(e: Throwable): String =
                when (e) {
                    is SocketTimeoutException -> ERROR_TIMEOUT
                    is IOException -> ERROR_NETWORK
                    else -> ERROR_UNKNOWN
                }
        }
    }
