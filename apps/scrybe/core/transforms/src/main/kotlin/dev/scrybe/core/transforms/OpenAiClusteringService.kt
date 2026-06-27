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

data class SessionSummary(
    val id: String,
    val title: String,
    val tags: List<String>,
    val transcriptPreview: String?,
)

data class ClusterSuggestion(
    val folderName: String,
    val sessionIds: List<String>,
)

class OpenAiClusteringService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val endpointResolver: OpenAiEndpointResolver,
    ) {
        suspend fun suggestClusters(
            sessions: List<SessionSummary>,
            existingFolderNames: List<String>,
            commonTags: List<String>,
        ): Result<List<ClusterSuggestion>> =
            runCatching {
                withContext(Dispatchers.IO) {
                    require(sessions.isNotEmpty()) {
                        "At least one recording is required for clustering"
                    }

                    val endpoint = endpointResolver.resolve()

                    val response =
                        executeResponseRequest(
                            endpoint = endpoint,
                            instructions = buildInstructions(),
                            userMessage =
                                buildUserMessage(
                                    sessions = sessions,
                                    existingFolderNames = existingFolderNames,
                                    commonTags = commonTags,
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
                        throw IOException("OpenAI API returned no clustering suggestions")
                    }

                    val payload =
                        json.decodeFromString(
                            ClusteringPayload.serializer(),
                            unwrapJsonEnvelope(outputText),
                        )

                    val validSessionIds = sessions.map { it.id }.toSet()
                    payload.clusters
                        .filter { it.folderName.isNotBlank() && it.sessionIds.isNotEmpty() }
                        .map { cluster ->
                            ClusterSuggestion(
                                folderName = cluster.folderName.trim(),
                                sessionIds = cluster.sessionIds.filter { it in validSessionIds },
                            )
                        }.filter { it.sessionIds.isNotEmpty() }
                }
            }

        private fun buildInstructions(): String =
            """
            You organize audio recordings into folders for the Android app Scrybe.

            Return only JSON with this shape:
            {"clusters":[{"folder_name":"...","session_ids":["...","..."]}]}

            Requirements:
            - Group recordings by topic, project, or purpose.
            - Prefer matching existing folder names when recordings fit them.
            - Use the existing tags and common patterns as context for grouping decisions.
            - Create 2 to 6 folders. Each recording should appear in exactly one folder.
            - Recordings that do not clearly fit any group can be left out.
            - Keep folder names short and descriptive (2-4 words).
            """.trimIndent()

        private fun buildUserMessage(
            sessions: List<SessionSummary>,
            existingFolderNames: List<String>,
            commonTags: List<String>,
        ): String =
            buildString {
                appendLine("Suggest folder groupings for these recordings.")
                appendLine()
                if (existingFolderNames.isNotEmpty()) {
                    appendLine("Existing folders: ${existingFolderNames.joinToString(", ")}")
                    appendLine()
                }
                if (commonTags.isNotEmpty()) {
                    appendLine("Common tags: ${commonTags.joinToString(", ")}")
                    appendLine()
                }
                appendLine("Recordings:")
                sessions.forEach { session ->
                    appendLine()
                    appendLine("ID: ${session.id}")
                    appendLine("Title: ${session.title}")
                    if (session.tags.isNotEmpty()) {
                        appendLine("Tags: ${session.tags.joinToString(", ")}")
                    }
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
                    maxOutputTokens = 1000,
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
        private data class ClusteringPayload(
            val clusters: List<ClusterEntry>,
        )

        @Serializable
        private data class ClusterEntry(
            @SerialName("folder_name") val folderName: String,
            @SerialName("session_ids") val sessionIds: List<String>,
        )

        private companion object {
            const val MODEL_NAME = "gpt-5-mini"
            const val MAX_PREVIEW_LENGTH = 200
            val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
