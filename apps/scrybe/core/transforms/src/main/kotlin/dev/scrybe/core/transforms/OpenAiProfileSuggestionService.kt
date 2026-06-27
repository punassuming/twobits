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

class OpenAiProfileSuggestionService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val endpointResolver: OpenAiEndpointResolver,
    ) {
        suspend fun suggestProfile(
            userRequest: String,
            existingName: String,
            existingDescription: String,
            existingSteps: List<String>,
            modelName: String,
        ): Result<ProfileSuggestion> =
            runCatching {
                withContext(Dispatchers.IO) {
                    require(
                        userRequest.isNotBlank() ||
                            existingName.isNotBlank() ||
                            existingDescription.isNotBlank() ||
                            existingSteps.any { it.isNotBlank() },
                    ) {
                        "Describe what the profile should do before asking for a suggestion"
                    }

                    val endpoint = endpointResolver.resolve()

                    val parsed =
                        executeResponseRequest(
                            endpoint = endpoint,
                            modelName = modelName,
                            instructions = buildInstructions(),
                            userMessage =
                                buildUserMessage(
                                    userRequest = userRequest,
                                    existingName = existingName,
                                    existingDescription = existingDescription,
                                    existingSteps = existingSteps,
                                ),
                        )
                    val outputText =
                        parsed.outputText?.takeIf { it.isNotBlank() }
                            ?: parsed.output
                                .orEmpty()
                                .flatMap { it.content.orEmpty() }
                                .filter { it.type == "output_text" }
                                .joinToString("\n") { it.text.orEmpty() }
                                .trim()

                    if (outputText.isBlank()) {
                        throw IOException("OpenAI API returned no profile suggestion")
                    }

                    val suggestion =
                        json.decodeFromString(
                            SuggestedProfilePayload.serializer(),
                            unwrapJsonEnvelope(outputText),
                        )
                    val normalizedSteps =
                        suggestion.steps
                            .map { it.trim() }
                            .filter { it.isNotBlank() }

                    require(suggestion.name.isNotBlank()) { "Suggested profile name was blank" }
                    require(normalizedSteps.isNotEmpty()) { "Suggested profile did not include any steps" }

                    ProfileSuggestion(
                        name = suggestion.name.trim(),
                        description = suggestion.description.trim(),
                        steps = normalizedSteps,
                        tokensUsed = parsed.usage?.totalTokens ?: 0,
                    )
                }
            }

        suspend fun testModel(
            modelName: String,
        ): Result<String> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val endpoint = endpointResolver.resolve()

                    val parsed =
                        executeResponseRequest(
                            endpoint = endpoint,
                            modelName = modelName,
                            instructions = "Reply with READY.",
                            userMessage = "Test the configured profile suggestion model.",
                        )

                    parsed.model ?: modelName
                }
            }

        private fun buildInstructions(): String =
            """
            You design text transformation profiles for the Android app Scrybe.

            Scrybe capabilities:
            - It records audio and transcribes it.
            - It can run one or more text transformation steps after transcription.
            - Each transform step is a prompt template that returns transformed text.
            - {{transcript}} inserts the original machine transcript.
            - {{combined_transcripts}} inserts all selected transcript text together when a bulk consolidation transform runs.
            - {{current_text}} and {{prior_output}} insert the latest intermediate text from the previous step.
            - Profiles must stay inside Scrybe's current capabilities. Do not require web browsing, external APIs, email sending, task creation, calendars, or file system actions.

            Return only JSON with this shape:
            {"name":"...","description":"...","steps":["...", "..."]}

            Requirements:
            - 1 to 3 steps.
            - Each step must be a concrete prompt Scrybe can send directly to a model.
            - Prefer {{transcript}} in the first step and {{current_text}} or {{prior_output}} in later steps.
            - Keep the prompts practical and concise.
            """.trimIndent()

        private fun buildUserMessage(
            userRequest: String,
            existingName: String,
            existingDescription: String,
            existingSteps: List<String>,
        ): String =
            buildString {
                appendLine("Create or refine a Scrybe transformation profile.")
                if (userRequest.isNotBlank()) {
                    appendLine()
                    appendLine("User request:")
                    appendLine(userRequest.trim())
                }
                if (existingName.isNotBlank() || existingDescription.isNotBlank() || existingSteps.any { it.isNotBlank() }) {
                    appendLine()
                    appendLine("Current draft:")
                    appendLine("Name: ${existingName.ifBlank { "(blank)" }}")
                    appendLine("Description: ${existingDescription.ifBlank { "(blank)" }}")
                    appendLine("Steps:")
                    if (existingSteps.any { it.isNotBlank() }) {
                        existingSteps.filter { it.isNotBlank() }.forEachIndexed { index, step ->
                            appendLine("${index + 1}. ${step.trim()}")
                        }
                    } else {
                        appendLine("(none)")
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
            modelName: String,
            instructions: String,
            userMessage: String,
        ): OpenAiResponseResponse {
            val requestBody =
                OpenAiResponseRequest(
                    model = modelName,
                    instructions = instructions,
                    input =
                        listOf(
                            ResponseInputMessage(
                                type = "message",
                                role = "user",
                                content =
                                    listOf(
                                        InputText(
                                            type = "input_text",
                                            text = userMessage,
                                        ),
                                    ),
                            ),
                        ),
                    maxOutputTokens = 800,
                )

            val request =
                Request
                    .Builder()
                    .url("${endpoint.baseUrl}/v1/responses")
                    .header("Authorization", "Bearer ${endpoint.authToken}")
                    .header("X-TwoBits-App", "scrybe")
                    .header("X-TwoBits-Op", "profile-suggest")
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
            val usage: ResponseUsage? = null,
        )

        @Serializable
        private data class ResponseUsage(
            @SerialName("total_tokens") val totalTokens: Int = 0,
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
        private data class SuggestedProfilePayload(
            val name: String,
            val description: String,
            val steps: List<String>,
        )

        private companion object {
            val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
