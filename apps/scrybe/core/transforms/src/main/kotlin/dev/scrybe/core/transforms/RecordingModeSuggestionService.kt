package dev.scrybe.core.transforms

import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.transcription.OpenAiEndpoint
import dev.scrybe.core.transcription.OpenAiEndpointResolver
import kotlinx.coroutines.Dispatchers
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
class RecordingModeSuggestionService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val endpointResolver: OpenAiEndpointResolver,
    ) {
        suspend fun suggestMode(transcriptText: String): Result<RecordingMode> =
            runCatching {
                withContext(Dispatchers.IO) {
                    require(transcriptText.isNotBlank()) { "Transcript required to classify mode" }
                    val endpoint = endpointResolver.resolve()
                    val modeDescriptions =
                        RecordingMode.entries.joinToString("\n") { "- ${it.name}: ${it.outputDescription}" }
                    val instructions =
                        """
                        Classify an audio recording transcript into one of the following types:
                        $modeDescriptions

                        Return only JSON: {"mode":"<TYPE_NAME>"}
                        Use exactly one of the TYPE_NAME values listed above.
                        """.trimIndent()
                    val userMessage = "Transcript:\n${transcriptText.take(800)}"
                    val rawText = executeApiCall(endpoint, instructions, userMessage)
                    val cleaned =
                        rawText
                            .trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()
                    val payload = json.decodeFromString(ModePayload.serializer(), cleaned)
                    runCatching { RecordingMode.valueOf(payload.mode.uppercase()) }.getOrDefault(RecordingMode.JOURNAL)
                }
            }

        private fun executeApiCall(
            endpoint: OpenAiEndpoint,
            instructions: String,
            userMessage: String,
        ): String {
            val requestBody =
                ApiRequest(
                    model = MODEL_NAME,
                    instructions = instructions,
                    input =
                        listOf(
                            InputMessage(
                                type = "message",
                                role = "user",
                                content = listOf(InputText(type = "input_text", text = userMessage)),
                            ),
                        ),
                )
            val request =
                Request
                    .Builder()
                    .url("${endpoint.baseUrl}/v1/responses")
                    .header("Authorization", "Bearer ${endpoint.authToken}")
                    .header("X-TwoBits-App", "scrybe")
                    .header("X-TwoBits-Op", "recording-mode")
                    .header("Content-Type", "application/json")
                    .post(json.encodeToString(ApiRequest.serializer(), requestBody).toRequestBody(JSON_MEDIA))
                    .build()
            return okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err =
                        response.body
                            ?.string()
                            .orEmpty()
                            .replace("\n", " ")
                            .take(500)
                    throw IOException("OpenAI error: ${response.code} $err")
                }
                val body = response.body?.string() ?: throw IOException("Empty response body")
                val resp = json.decodeFromString(ApiResponse.serializer(), body)
                resp.outputText
                    ?: resp.output
                        .orEmpty()
                        .flatMap { it.content.orEmpty() }
                        .firstOrNull { it.type == "output_text" }
                        ?.text
                    ?: throw IOException("No output text in response")
            }
        }

        @Serializable private data class ApiRequest(
            val model: String,
            val instructions: String,
            val input: List<InputMessage>,
        )

        @Serializable private data class InputMessage(
            val type: String,
            val role: String,
            val content: List<InputText>,
        )

        @Serializable private data class InputText(
            val type: String,
            val text: String,
        )

        @Serializable
        private data class ApiResponse(
            @SerialName("output_text") val outputText: String? = null,
            val output: List<OutputItem>? = null,
        )

        @Serializable private data class OutputItem(
            val content: List<OutputContent>? = null,
        )

        @Serializable private data class OutputContent(
            val type: String? = null,
            val text: String? = null,
        )

        @Serializable private data class ModePayload(
            val mode: String,
        )

        private companion object {
            const val MODEL_NAME = "gpt-5-mini"
            val JSON_MEDIA = "application/json".toMediaType()
        }
    }
