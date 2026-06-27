package dev.scrybe.core.transforms

import dev.scrybe.core.model.SessionTask
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
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class OpenAiTaskExtractionService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val endpointResolver: OpenAiEndpointResolver,
    ) {
        suspend fun extractTasks(
            sessionId: String,
            transcriptText: String,
        ): Result<List<SessionTask>> =
            runCatching {
                withContext(Dispatchers.IO) {
                    require(transcriptText.isNotBlank()) { "Transcript is required for task extraction" }

                    val endpoint = endpointResolver.resolve()

                    val response = executeRequest(endpoint, transcriptText)

                    val outputText =
                        response.outputText?.takeIf { it.isNotBlank() }
                            ?: response.output
                                .orEmpty()
                                .flatMap { it.content.orEmpty() }
                                .filter { it.type == "output_text" }
                                .joinToString("\n") { it.text.orEmpty() }
                                .trim()

                    if (outputText.isBlank()) throw IOException("OpenAI returned no task extraction output")

                    val payload =
                        json.decodeFromString(TaskPayload.serializer(), unwrapJsonEnvelope(outputText))
                    val now = Instant.now()
                    payload.tasks.map { item ->
                        SessionTask(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            text = item.text,
                            assignee = item.assignee?.takeIf { it.isNotBlank() },
                            dueLabel = item.due?.takeIf { it.isNotBlank() },
                            isDone = false,
                            createdAt = now,
                        )
                    }
                }
            }

        private fun executeRequest(
            endpoint: OpenAiEndpoint,
            transcriptText: String,
        ): TaskResponse {
            val requestBody =
                TaskRequest(
                    model = MODEL_NAME,
                    instructions = INSTRUCTIONS,
                    input =
                        listOf(
                            InputMessage(
                                type = "message",
                                role = "user",
                                content =
                                    listOf(
                                        InputText(
                                            type = "input_text",
                                            text = "Extract action items from this transcript:\n\n$transcriptText",
                                        ),
                                    ),
                            ),
                        ),
                    maxOutputTokens = 1500,
                )

            val request =
                Request
                    .Builder()
                    .url("${endpoint.baseUrl}/v1/responses")
                    .header("Authorization", "Bearer ${endpoint.authToken}")
                    .header("Content-Type", "application/json")
                    .post(
                        json
                            .encodeToString(TaskRequest.serializer(), requestBody)
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
                            if (errorBody.isNotBlank()) " — $errorBody" else "",
                    )
                }
                val body = response.body?.string() ?: throw IOException("Empty response body")
                return json.decodeFromString(TaskResponse.serializer(), body)
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

        @Serializable
        private data class TaskRequest(
            val model: String,
            val instructions: String,
            val input: List<InputMessage>,
            @SerialName("max_output_tokens") val maxOutputTokens: Int? = null,
        )

        @Serializable
        private data class InputMessage(
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
        private data class TaskResponse(
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
        private data class TaskPayload(
            val tasks: List<TaskItem>,
        )

        @Serializable
        private data class TaskItem(
            val text: String,
            val assignee: String? = null,
            val due: String? = null,
        )

        private companion object {
            const val MODEL_NAME = "gpt-5-mini"
            val JSON_MEDIA_TYPE = "application/json".toMediaType()
            val INSTRUCTIONS =
                """
                Extract all action items and tasks from the transcript.

                Return only JSON with this exact shape:
                {"tasks":[{"text":"...","assignee":"...","due":"..."}]}

                Rules:
                - Include only concrete, assignable actions — not observations or statements.
                - "assignee" is the name of the person responsible (null if unspecified).
                - "due" is a human-readable due date or deadline (null if unspecified).
                - Return an empty array if there are no tasks.
                - Do not include punctuation-heavy phrases.
                """.trimIndent()
        }
    }
