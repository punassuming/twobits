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

class OpenAiTagSuggestionService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val endpointResolver: OpenAiEndpointResolver,
    ) {
        suspend fun suggestTags(
            title: String,
            transcriptText: String,
            existingTags: List<String>,
        ): Result<List<String>> =
            runCatching {
                withContext(Dispatchers.IO) {
                    require(title.isNotBlank() || transcriptText.isNotBlank()) {
                        "A transcript or title is required before suggesting tags"
                    }

                    val endpoint = endpointResolver.resolve()

                    val response =
                        executeResponseRequest(
                            endpoint = endpoint,
                            instructions = buildInstructions(),
                            userMessage =
                                buildUserMessage(
                                    title = title,
                                    transcriptText = transcriptText,
                                    existingTags = existingTags,
                                ),
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
                        throw IOException("OpenAI API returned no tag suggestions")
                    }

                    json
                        .decodeFromString(TagSuggestionPayload.serializer(), unwrapJsonEnvelope(outputText))
                        .tags
                        .map(::normalizeTag)
                        .filter(String::isNotBlank)
                        .distinct()
                        .take(MAX_TAGS)
                }
            }

        private fun buildInstructions(): String =
            """
            Suggest concise tags for a recording in the Android app Scrybe.

            Return only JSON with this shape:
            {"tags":["...","..."]}

            Requirements:
            - Return 3 to 8 tags.
            - Keep tags short, lowercase, and human-readable.
            - Prefer topics, people, project names, meeting types, and action-oriented labels when obvious.
            - Do not include punctuation-heavy phrases or sentence fragments.
            - Reuse or refine existing tags when they still fit.
            """.trimIndent()

        private fun buildUserMessage(
            title: String,
            transcriptText: String,
            existingTags: List<String>,
        ): String =
            buildString {
                appendLine("Suggest tags for this recording.")
                appendLine()
                appendLine("Title: ${title.ifBlank { "(untitled)" }}")
                appendLine("Existing tags: ${existingTags.ifEmpty { listOf("(none)") }.joinToString(", ")}")
                appendLine()
                appendLine("Transcript:")
                appendLine(transcriptText.ifBlank { "(none)" })
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
                    maxOutputTokens = 400,
                )

            val request =
                Request
                    .Builder()
                    .url("${endpoint.baseUrl}/v1/responses")
                    .header("Authorization", "Bearer ${endpoint.authToken}")
                    .header("Content-Type", "application/json")
                    .post(json.encodeToString(OpenAiResponseRequest.serializer(), requestBody).toRequestBody(JSON_MEDIA_TYPE))
                    .build()

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

        private fun normalizeTag(value: String): String =
            value
                .trim()
                .lowercase()
                .replace(Regex("[^a-z0-9&+/#\\- ]"), "")
                .replace(Regex("\\s+"), " ")

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
        private data class TagSuggestionPayload(
            val tags: List<String>,
        )

        private companion object {
            const val MODEL_NAME = "gpt-5-mini"
            const val MAX_TAGS = 8
            val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
