package dev.scrybe.core.transforms

import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transcription.ApiKeyProvider
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

class OpenAiSemanticSearchService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val apiKeyProvider: ApiKeyProvider,
    ) {
        suspend fun rankByRelevance(
            query: String,
            sessions: List<SessionSummary>,
        ): Result<List<String>> =
            runCatching {
                withContext(Dispatchers.IO) {
                    require(sessions.isNotEmpty()) { "No recordings to search" }
                    require(query.isNotBlank()) { "Search query must not be blank" }

                    val apiKey =
                        apiKeyProvider.getApiKey(ProviderType.OPENAI)
                            ?: throw IllegalStateException("No API key configured for OpenAI")

                    val response =
                        executeResponseRequest(
                            apiKey = apiKey,
                            instructions = buildInstructions(),
                            userMessage = buildUserMessage(query, sessions),
                        )

                    val outputText =
                        response.outputText?.takeIf { it.isNotBlank() }
                            ?: response.output
                                .orEmpty()
                                .flatMap { it.content.orEmpty() }
                                .filter { it.type == "output_text" }
                                .joinToString("\n") { it.text.orEmpty() }
                                .trim()

                    if (outputText.isBlank()) throw IOException("OpenAI returned no search results")

                    val payload =
                        json.decodeFromString(
                            SearchPayload.serializer(),
                            unwrapJsonEnvelope(outputText),
                        )

                    val validIds = sessions.map { it.id }.toSet()
                    payload.rankedIds.filter { it in validIds }
                }
            }

        private fun buildInstructions(): String =
            """
            You are a semantic search engine for audio recordings in the Scrybe app.

            Given a search query and a list of recordings (with titles, tags, and transcript previews),
            return the IDs of the recordings most relevant to the query, ordered from most to least relevant.

            Return only JSON in this exact shape:
            {"ranked_ids":["id1","id2","id3"]}

            Rules:
            - Only include recordings that are genuinely relevant to the query.
            - Omit irrelevant recordings entirely.
            - Order by relevance (most relevant first).
            """.trimIndent()

        private fun buildUserMessage(
            query: String,
            sessions: List<SessionSummary>,
        ): String =
            buildString {
                appendLine("Search query: $query")
                appendLine()
                appendLine("Recordings:")
                sessions.forEach { session ->
                    appendLine()
                    appendLine("ID: ${session.id}")
                    appendLine("Title: ${session.title}")
                    if (session.tags.isNotEmpty()) appendLine("Tags: ${session.tags.joinToString(", ")}")
                    session.transcriptPreview?.takeIf { it.isNotBlank() }?.let { preview ->
                        appendLine("Preview: ${preview.take(MAX_PREVIEW_LENGTH)}")
                    }
                }
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
            apiKey: String,
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
                )

            val request =
                Request
                    .Builder()
                    .url("https://api.openai.com/v1/responses")
                    .header("Authorization", "Bearer $apiKey")
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
        private data class SearchPayload(
            @SerialName("ranked_ids") val rankedIds: List<String>,
        )

        private companion object {
            const val MODEL_NAME = "gpt-5-mini"
            const val MAX_PREVIEW_LENGTH = 200
            val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
