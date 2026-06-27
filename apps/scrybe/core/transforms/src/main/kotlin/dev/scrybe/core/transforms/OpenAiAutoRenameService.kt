package dev.scrybe.core.transforms

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

class OpenAiAutoRenameService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val endpointResolver: OpenAiEndpointResolver,
    ) {
        suspend fun suggestTitle(
            transcriptText: String,
            currentTitle: String,
        ): Result<String> =
            runCatching {
                withContext(Dispatchers.IO) {
                    require(transcriptText.isNotBlank()) {
                        "A transcript is required to suggest a title"
                    }

                    val endpoint = endpointResolver.resolve()

                    val response =
                        executeResponseRequest(
                            endpoint = endpoint,
                            instructions = buildInstructions(),
                            userMessage = buildUserMessage(transcriptText, currentTitle),
                        )

                    val outputText =
                        response.outputText?.takeIf { it.isNotBlank() }
                            ?: response.output
                                .orEmpty()
                                .flatMap { it.content.orEmpty() }
                                .filter { it.type == "output_text" }
                                .joinToString("\n") { it.text.orEmpty() }
                                .trim()

                    if (outputText.isBlank()) {
                        throw IOException("OpenAI API returned no title suggestion")
                    }

                    val payload =
                        json.decodeFromString(TitlePayload.serializer(), unwrapJsonEnvelope(outputText))
                    payload.title.trim().ifBlank { throw IOException("Suggested title was blank") }
                }
            }

        private fun buildInstructions(): String =
            """
            Suggest a concise, descriptive title for an audio recording in the Android app Scrybe.

            Return only JSON with this shape:
            {"title":"..."}

            Requirements:
            - Keep the title short (2 to 7 words).
            - Capture the main topic, meeting type, or purpose of the recording.
            - Use title case.
            - Do not include quotation marks or trailing punctuation.
            """.trimIndent()

        private fun buildUserMessage(
            transcriptText: String,
            currentTitle: String,
        ): String =
            buildString {
                appendLine("Suggest a title for this recording.")
                appendLine()
                appendLine("Current title: ${currentTitle.ifBlank { "(untitled)" }}")
                appendLine()
                appendLine("Transcript:")
                appendLine(transcriptText.take(MAX_TRANSCRIPT_LENGTH))
            }

        private fun unwrapJsonEnvelope(value: String): String {
            val trimmed = value.trim()
            if (!trimmed.startsWith("```")) return trimmed
            return trimmed
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        }

        private fun executeResponseRequest(
            endpoint: OpenAiEndpoint,
            instructions: String,
            userMessage: String,
        ): OpenAiResponseResponse {
            val requestBody =
                OpenAiResponseRequest(
                    model = MODEL_NAME,
                    instructions = instructions,
                    input =
                        listOf(
                            ResponseInputMessage(
                                type = "message",
                                role = "user",
                                content = listOf(InputText(type = "input_text", text = userMessage)),
                            ),
                        ),
                    maxOutputTokens = 200,
                )

            val request =
                Request
                    .Builder()
                    .url("${endpoint.baseUrl}/v1/responses")
                    .header("Authorization", "Bearer ${endpoint.authToken}")
                    .header("X-TwoBits-App", "scrybe")
                    .header("X-TwoBits-Op", "auto-rename")
                    .header("Content-Type", "application/json")
                    .post(
                        json
                            .encodeToString(OpenAiResponseRequest.serializer(), requestBody)
                            .toRequestBody(JSON_MEDIA_TYPE),
                    ).build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody =
                        response.body
                            ?.string()
                            .orEmpty()
                            .replace("\n", " ")
                            .take(500)
                    throw IOException(
                        "OpenAI API error: ${response.code} ${response.message}" +
                            if (errorBody.isNotBlank()) " - $errorBody" else "",
                    )
                }

                val body = response.body?.string() ?: throw IOException("Empty response body")
                return json.decodeFromString(OpenAiResponseResponse.serializer(), body)
            }
        }

        @Serializable
        private data class OpenAiResponseRequest(
            val model: String,
            val instructions: String,
            val input: List<ResponseInputMessage>,
            @SerialName("max_output_tokens") val maxOutputTokens: Int? = null,
        )

        @Serializable
        private data class ResponseInputMessage(
            val type: String,
            val role: String,
            val content: List<InputText>,
        )

        @Serializable
        private data class InputText(
            val type: String,
            val text: String,
        )

        @Serializable
        private data class OpenAiResponseResponse(
            val model: String? = null,
            @SerialName("output_text") val outputText: String? = null,
            val output: List<OutputItem>? = null,
        )

        @Serializable
        private data class OutputItem(
            val content: List<OutputContent>? = null,
        )

        @Serializable
        private data class OutputContent(
            val type: String? = null,
            val text: String? = null,
        )

        @Serializable
        private data class TitlePayload(
            val title: String,
        )

        private companion object {
            const val MODEL_NAME = "gpt-5-mini"
            const val MAX_TRANSCRIPT_LENGTH = 800
            val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
