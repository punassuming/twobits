package com.shelfsnap.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shelfsnap.app.data.listing.ListingCopy
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls the OpenAI Chat Completions API to AI-refine a platform-specific listing copy.
 * Returns the existing [current] copy unchanged on any error so no data is lost.
 */
@Singleton
class ListingGenerationService
    @Inject
    constructor() {
        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .build()

        private val gson = Gson()
        private val json = "application/json; charset=utf-8".toMediaType()

        suspend fun refine(
            item: Item,
            platform: Platform,
            current: ListingCopy,
            openAiKey: String,
            openAiBaseUrl: String = "https://api.openai.com",
            openAiAuthHeader: String = "Bearer $openAiKey",
            model: String = DEFAULT_MODEL,
        ): ListingCopy =
            withContext(Dispatchers.IO) {
                runCatching {
                    val tipsText = platform.listingTips.replace(" · ", "\n• ")
                    val systemPrompt =
                        "You are a resale listing copywriter for ${platform.displayName}. " +
                            "Platform tips:\n• $tipsText\n" +
                            "Title character limit: ${platform.titleCharLimit}. " +
                            "Write a specific, keyword-rich title in the style experienced resale sellers use — " +
                            "lead with brand and model/product name, then work in the most distinguishing " +
                            "attribute available (size, color, or material) and a condition qualifier if space " +
                            "allows. Avoid generic titles like just the category or brand alone when a more " +
                            "specific product name is available in the fields below. " +
                            "Respond with ONLY valid JSON: " +
                            "{\"title\":\"...\",\"description\":\"...\",\"condition\":\"...\",\"shipping\":\"...\"}"
                    val userMessage =
                        buildString {
                            if (item.brand.isNotBlank()) appendLine("Brand: ${item.brand}")
                            if (item.model.isNotBlank()) appendLine("Model: ${item.model}")
                            appendLine("Category: ${item.category}")
                            if (item.size.isNotBlank()) appendLine("Size: ${item.size}")
                            if (item.color.isNotBlank()) appendLine("Color: ${item.color}")
                            appendLine("Condition: ${item.condition.name.lowercase()}")
                            if (item.description.isNotBlank()) appendLine("Description: ${item.description}")
                            if (item.tags.isNotEmpty()) appendLine("Tags: ${item.tags.joinToString(", ")}")
                            val price = item.marketResearch.suggestedPrices[platform.key] ?: item.estimatedValue
                            if (price > 0) appendLine("Price: ${"$%.2f".format(price)}")
                            appendLine("Current title: ${current.title}")
                        }
                    val body =
                        JsonObject().apply {
                            addProperty("model", model)
                            add(
                                "messages",
                                gson.toJsonTree(
                                    listOf(
                                        mapOf("role" to "system", "content" to systemPrompt),
                                        mapOf("role" to "user", "content" to userMessage),
                                    ),
                                ),
                            )
                            addProperty("max_tokens", 400)
                            addProperty("temperature", 0.4)
                        }
                    val request =
                        Request
                            .Builder()
                            .url("$openAiBaseUrl/v1/chat/completions")
                            .addHeader("Authorization", openAiAuthHeader)
                            .addHeader("X-TwoBits-App", "shelfsnap")
                            .addHeader("X-TwoBits-Op", "listing")
                            .addHeader("Content-Type", "application/json")
                            .post(gson.toJson(body).toRequestBody(json))
                            .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use current
                        val content =
                            JsonParser
                                .parseString(response.body?.string() ?: "")
                                .asJsonObject
                                .getAsJsonArray("choices")
                                .get(0)
                                .asJsonObject
                                .getAsJsonObject("message")
                                .get("content")
                                .asString
                                .removePrefix("```json")
                                .removePrefix("```")
                                .removeSuffix("```")
                                .trim()
                        val obj = JsonParser.parseString(content).asJsonObject
                        current.copy(
                            title = obj.get("title")?.asString?.take(platform.titleCharLimit) ?: current.title,
                            description = obj.get("description")?.asString ?: current.description,
                            condition = obj.get("condition")?.asString ?: current.condition,
                            shipping = obj.get("shipping")?.asString ?: current.shipping,
                        )
                    }
                }.getOrElse {
                    Log.w(TAG, "Listing refinement failed: ${it.javaClass.simpleName} — keeping current copy")
                    current
                }
            }

        companion object {
            private const val TAG = "ListingGenerationService"
            private const val DEFAULT_MODEL = "gpt-5.4-mini"
        }
    }
