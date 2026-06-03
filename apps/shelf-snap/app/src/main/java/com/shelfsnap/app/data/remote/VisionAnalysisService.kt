package com.shelfsnap.app.data.remote

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
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
 * Uses the OpenAI Chat Completions API (GPT-4o or gpt-4-vision-preview) to analyse
 * one or more photos of a donation item and return structured metadata.
 *
 * The API key is supplied at call-time so the user can update it at any point via Settings.
 */
@Singleton
class VisionAnalysisService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val json = "application/json; charset=utf-8".toMediaType()

    /**
     * Analyses [photoPaths] and returns a [DraftItemResult].
     * Returns a result with [DraftItemResult.error] set if the call fails.
     */
    suspend fun analyse(photoPaths: List<String>, apiKey: String, model: String): DraftItemResult =
        withContext(Dispatchers.IO) {
            // Fail fast on a missing/obviously-invalid key — no network round-trip needed.
            if (!ApiKeyValidator.isValid(apiKey)) {
                Log.w(TAG, "Analysis aborted: API key missing or invalid format")
                return@withContext errorResult(ERROR_INVALID_KEY)
            }
            runCatching {
                val imageContents = photoPaths.map { path ->
                    val b64 = encodeImageToBase64(path)
                    JsonObject().apply {
                        addProperty("type", "image_url")
                        add("image_url", JsonObject().apply {
                            addProperty("url", "data:image/jpeg;base64,$b64")
                            addProperty("detail", "low")
                        })
                    }
                }

                val systemPrompt = """
                    You are an expert at evaluating household goods for charitable donation.
                    Given one or more photos of a single item, respond ONLY with valid JSON in this exact schema:
                    {
                      "category": "<short category, e.g. Clothing, Electronics, Books, Furniture, Toys, Kitchenware, Other>",
                      "description": "<one-sentence description of the item>",
                      "condition": "<one of: EXCELLENT, GOOD, FAIR, POOR>",
                      "estimatedValue": <number, USD resale/donation estimate>,
                      "confidencePercent": <integer 0-100>
                    }
                    Be concise. Do not include any explanation outside the JSON object.
                """.trimIndent()

                val userContent = com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", "text")
                        addProperty(
                            "text",
                            "Please analyse the item in the following photo(s) and return the JSON."
                        )
                    })
                    imageContents.forEach { add(it) }
                }

                val messages = com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", systemPrompt)
                    })
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("content", userContent)
                    })
                }

                val requestBody = JsonObject().apply {
                    addProperty("model", model)
                    add("messages", messages)
                    addProperty("max_tokens", 300)
                }

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
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
                    parseResponse(body)
                }
            }.getOrElse { e ->
                Log.w(TAG, "OpenAI request threw ${e.javaClass.simpleName}")
                errorResult(friendlyNetworkError(e))
            }
        }

    private fun parseResponse(responseJson: String): DraftItemResult {
        return runCatching {
            val root = JsonParser.parseString(responseJson).asJsonObject
            val content = root
                .getAsJsonArray("choices")
                .get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString

            // Strip possible markdown code fences
            val cleaned = content
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val obj = JsonParser.parseString(cleaned).asJsonObject
            DraftItemResult(
                category = obj.get("category")?.asString ?: "Other",
                description = obj.get("description")?.asString ?: "",
                condition = runCatching {
                    Condition.valueOf(obj.get("condition")?.asString ?: "GOOD")
                }.getOrDefault(Condition.GOOD),
                estimatedValue = obj.get("estimatedValue")?.asDouble ?: 0.0,
                confidencePercent = obj.get("confidencePercent")?.asInt ?: 0
            )
        }.getOrElse { e ->
            Log.w(TAG, "Failed to parse OpenAI response: ${e.javaClass.simpleName}")
            errorResult(ERROR_PARSE)
        }
    }

    /**
     * Scales down and base64-encodes a JPEG image file to keep request size reasonable.
     */
    private fun encodeImageToBase64(path: String): String {
        val file = File(path)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        val maxDim = 512
        val scale = maxOf(
            options.outWidth / maxDim,
            options.outHeight / maxDim,
            1
        )
        val scaled = BitmapFactory.Options().apply {
            inSampleSize = scale
        }
        val bitmap = BitmapFactory.decodeFile(path, scaled)
        val out = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        bitmap.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun errorResult(message: String) = DraftItemResult(
        category = "",
        description = "",
        condition = Condition.GOOD,
        estimatedValue = 0.0,
        confidencePercent = 0,
        error = message
    )

    companion object {
        private const val TAG = "VisionAnalysisService"

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
        internal fun friendlyHttpError(code: Int): String = when {
            code == 401 || code == 403 -> ERROR_INVALID_KEY
            code == 429 -> ERROR_RATE_LIMITED
            code in 500..599 -> ERROR_UNAVAILABLE
            else -> ERROR_UNAVAILABLE
        }

        /** Maps a thrown exception to a user-facing message. */
        internal fun friendlyNetworkError(e: Throwable): String = when (e) {
            is SocketTimeoutException -> ERROR_TIMEOUT
            is IOException -> ERROR_NETWORK
            else -> ERROR_UNKNOWN
        }
    }
}
