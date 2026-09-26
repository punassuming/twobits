package dev.scrybe.core.transcription

import com.twobits.debuglog.DebugLogEntry
import com.twobits.debuglog.DebugLogEntryType
import com.twobits.debuglog.DebugLogStore
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
        private val debugLogStore: DebugLogStore,
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
                    unwrapJson(raw)
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
                    unwrapJson(raw)
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
                    // gpt-5.4-mini is a reasoning model: reasoning tokens count against
                    // max_output_tokens, and hitting the cap mid-reasoning returns
                    // status="incomplete" with EMPTY output text — callers used to swallow this
                    // via .ifBlank into a fake "no sentiment/topics" result on every recording;
                    // callOpenAi() now throws on it instead (mirroring
                    // OpenAiDiarizationService.callDiarizationLlm). Moved from gpt-5-mini to
                    // gpt-5.4-mini for its larger context/output budget; the cap here is raised
                    // accordingly to use that headroom rather than carrying over the old model's
                    // tighter tuning. Low effort keeps thinking terse and leaves more of the cap
                    // for actual output.
                    maxOutputTokens = 4000,
                    reasoning = InsightReasoningConfig(effort = "low"),
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
            val startedAtMs = System.currentTimeMillis()

            suspend fun recordOnce(
                success: Boolean,
                httpStatus: Int?,
                snippet: String,
                stackTrace: String? = null,
            ) {
                if (recorded) return
                // A failure is recorded whether or not "AI call debug" is on: it is rare, and it is
                // the one outcome anyone debugging needs to see. Only the success path stays opt-in.
                // This is the same gap that was closed for the on-device paths, left here in the
                // cloud ones — a failed cloud transcription used to log nothing at all by default.
                if (success && !debugEnabled) return
                recorded = true
                debugLogStore.record(
                    DebugLogEntry(
                        timestampMs = System.currentTimeMillis(),
                        type = DebugLogEntryType.AI_CALL,
                        op = op,
                        endpoint = "/v1/responses",
                        model = MODEL_NAME,
                        requestSummary = "prompt ${userPrompt.length} chars",
                        success = success,
                        httpStatus = httpStatus,
                        responseSnippet = snippet,
                        stackTrace = stackTrace,
                        durationMs = System.currentTimeMillis() - startedAtMs,
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
                    if (text.isBlank()) {
                        val detail =
                            listOfNotNull(
                                parsed.status?.let { "status=$it" },
                                parsed.incompleteDetails?.reason?.let { "reason=$it" },
                            ).joinToString(", ").ifBlank { "no detail" }
                        recordOnce(success = false, httpStatus = response.code, snippet = "empty model response ($detail)")
                        throw IOException(
                            "Insight generation returned no output ($detail) — " +
                                "the model likely spent its whole output-token budget on reasoning.",
                        )
                    }
                    recordOnce(success = true, httpStatus = response.code, snippet = "${text.length} chars")
                    text
                }
            }.getOrElse { error ->
                recordOnce(
                    success = false,
                    httpStatus = null,
                    snippet = "${error.javaClass.simpleName}: ${error.message}",
                    stackTrace = error.stackTraceToString(),
                )
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

        // No default values on encode-side fields: the shared Json doesn't set
        // encodeDefaults = true, so a defaulted field would be silently dropped from the wire.
        @Serializable
        private data class InsightRequest(
            val model: String,
            val input: List<InsightInputMessage>,
            @SerialName("max_output_tokens") val maxOutputTokens: Int,
            val reasoning: InsightReasoningConfig,
        )

        @Serializable
        private data class InsightReasoningConfig(
            val effort: String,
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
            val status: String? = null,
            @SerialName("incomplete_details") val incompleteDetails: IncompleteDetails? = null,
        )

        @Serializable
        private data class IncompleteDetails(
            val reason: String? = null,
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
            const val MODEL_NAME = "gpt-5.4-mini"
            val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
