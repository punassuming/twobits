package dev.scrybe.core.transcription

import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiInsightService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val endpointResolver: OpenAiEndpointResolver,
        private val aiCallDebugStore: AiCallDebugStore,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) : InsightService {
        override suspend fun analyzeSentiment(
            transcriptText: String,
            durationMs: Long,
            providerType: ProviderType,
        ): Result<String> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val endpoint = endpointResolver.resolve()
                    val debugEnabled = preferencesDataStore.debugDiarization.first()
                    val prompt =
                        """
                        Analyze the sentiment of this transcript over time. Duration: ${durationMs}ms.
                        Return ONLY a JSON array: [{"startMs":0,"endMs":$durationMs,"sentiment":"NEUTRAL"}]
                        Use POSITIVE, NEGATIVE, or NEUTRAL. Cover the entire duration without gaps.
                        Transcript: ${transcriptText.take(800)}
                        """.trimIndent()
                    val raw = callOpenAi(endpoint, prompt, "insight-sentiment", debugEnabled)
                    unwrapJson(raw).ifBlank { """[{"startMs":0,"endMs":$durationMs,"sentiment":"NEUTRAL"}]""" }
                }
            }

        override suspend fun extractTopics(
            transcriptText: String,
            durationMs: Long,
            providerType: ProviderType,
        ): Result<String> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val endpoint = endpointResolver.resolve()
                    val debugEnabled = preferencesDataStore.debugDiarization.first()
                    val prompt =
                        """
                        Extract key topics from this transcript. Estimate when each topic is discussed within ${durationMs}ms.
                        Return ONLY a JSON array: [{"timeMs":1000,"label":"topic name"}]
                        Keep labels short (2-4 words). Return 5-15 topics.
                        Transcript: ${transcriptText.take(1200)}
                        """.trimIndent()
                    val raw = callOpenAi(endpoint, prompt, "insight-topics", debugEnabled)
                    unwrapJson(raw).ifBlank { "[]" }
                }
            }

        private suspend fun callOpenAi(
            endpoint: OpenAiEndpoint,
            userPrompt: String,
            op: String,
            debugEnabled: Boolean,
        ): String {
            val requestBody =
                InsightRequest(
                    model = MODEL_NAME,
                    input =
                        listOf(
                            InsightInputMessage(
                                type = "message",
                                role = "user",
                                content = listOf(InsightInputText(type = "input_text", text = userPrompt)),
                            ),
                        ),
                    maxOutputTokens = 600,
                )
            val request =
                Request
                    .Builder()
                    .url("${endpoint.baseUrl}/v1/responses")
                    .header("Authorization", "Bearer ${endpoint.authToken}")
                    .header("X-TwoBits-App", "scrybe")
                    .header("X-TwoBits-Op", "insight")
                    .header("Content-Type", "application/json")
                    .post(
                        json
                            .encodeToString(InsightRequest.serializer(), requestBody)
                            .toRequestBody(JSON_MEDIA_TYPE),
                    ).build()
            var recorded = false

            suspend fun recordOnce(
                success: Boolean,
                httpStatus: Int?,
                snippet: String,
            ) {
                if (!debugEnabled || recorded) return
                recorded = true
                aiCallDebugStore.record(
                    AiCallDebugEntry(
                        timestampMs = System.currentTimeMillis(),
                        op = op,
                        endpoint = "/v1/responses",
                        model = MODEL_NAME,
                        requestSummary = "prompt ${userPrompt.length} chars",
                        success = success,
                        httpStatus = httpStatus,
                        responseSnippet = snippet,
                    ),
                )
            }

            return runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val err =
                            response.body
                                ?.string()
                                .orEmpty()
                                .take(400)
                        recordOnce(success = false, httpStatus = response.code, snippet = err)
                        throw IOException("OpenAI insight error: ${response.code} - $err")
                    }
                    val body = response.body?.string() ?: throw IOException("Empty response")
                    val parsed = json.decodeFromString(InsightResponse.serializer(), body)
                    val text =
                        parsed.outputText
                            ?: parsed.output
                                .orEmpty()
                                .flatMap { it.content.orEmpty() }
                                .filter { it.type == "output_text" }
                                .joinToString("\n") { it.text.orEmpty() }
                                .trim()
                    recordOnce(success = true, httpStatus = response.code, snippet = "${text.length} chars")
                    text
                }
            }.getOrElse { error ->
                recordOnce(success = false, httpStatus = null, snippet = "${error.javaClass.simpleName}: ${error.message}")
                throw error
            }
        }

        private fun unwrapJson(value: String): String =
            value
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

        @Serializable
        private data class InsightRequest(
            val model: String,
            val input: List<InsightInputMessage>,
            @SerialName("max_output_tokens") val maxOutputTokens: Int? = null,
        )

        @Serializable
        private data class InsightInputMessage(
            val type: String,
            val role: String,
            val content: List<InsightInputText>,
        )

        @Serializable
        private data class InsightInputText(
            val type: String,
            val text: String,
        )

        @Serializable
        private data class InsightResponse(
            @SerialName("output_text") val outputText: String? = null,
            val output: List<InsightOutputItem>? = null,
        )

        @Serializable
        private data class InsightOutputItem(
            val content: List<InsightOutputContent>? = null,
        )

        @Serializable
        private data class InsightOutputContent(
            val type: String? = null,
            val text: String? = null,
        )

        private companion object {
            const val MODEL_NAME = "gpt-5-mini"
            val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
