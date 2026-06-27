package dev.scrybe.core.transforms

import dev.scrybe.core.model.OpenAiTransformModel
import dev.scrybe.core.model.ProviderType
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

class OpenAiTransformationProvider
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val endpointResolver: OpenAiEndpointResolver,
    ) : TransformationProvider {
        override val providerType: ProviderType = ProviderType.OPENAI

        override suspend fun transform(input: TransformInput): Result<TransformResult> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val endpoint = endpointResolver.resolve()

                    val modelName =
                        input.modelName?.takeIf { it.isNotBlank() }
                            ?: OpenAiTransformModel.default.apiName

                    val requestBody =
                        OpenAiResponseRequest(
                            model = modelName,
                            instructions = renderInstructions(input),
                            input =
                                listOf(
                                    ResponseInputMessage(
                                        type = "message",
                                        role = "user",
                                        content =
                                            listOf(
                                                InputText(
                                                    type = "input_text",
                                                    text = buildUserMessage(input),
                                                ),
                                            ),
                                    ),
                                ),
                            maxOutputTokens = 2000,
                        )

                    val request =
                        Request
                            .Builder()
                            .url("${endpoint.baseUrl}/v1/responses")
                            .header("Authorization", "Bearer ${endpoint.authToken}")
                            .header("Content-Type", "application/json")
                            .post(json.encodeToString(OpenAiResponseRequest.serializer(), requestBody).toRequestBody(JSON_MEDIA_TYPE))
                            .build()

                    val response = okHttpClient.newCall(request).execute()
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
                    val parsed = json.decodeFromString(OpenAiResponseResponse.serializer(), body)
                    val outputText =
                        parsed.outputText?.takeIf { it.isNotBlank() }
                            ?: parsed.output
                                .orEmpty()
                                .flatMap { it.content.orEmpty() }
                                .filter { it.type == "output_text" }
                                .joinToString("\n") { it.text.orEmpty() }
                                .trim()

                    if (outputText.isBlank()) {
                        throw IOException("OpenAI API returned no transformed text")
                    }

                    TransformResult(
                        transformedText = outputText,
                        modelName = parsed.model,
                    )
                }
            }

        private fun renderInstructions(input: TransformInput): String = buildTemplate(input)

        private fun buildUserMessage(input: TransformInput): String =
            if (containsTranscriptPlaceholder(input.systemPrompt)) {
                "Follow the provided prompt template exactly and return only the transformed text."
            } else {
                buildString {
                    appendLine("Use the instructions above to transform this recording.")
                    appendLine("Return only the transformed text.")
                    appendLine()
                    appendLine("Current text:")
                    append(input.currentText)
                }
            }

        private fun buildTemplate(input: TransformInput): String {
            val replacements =
                mapOf(
                    TRANSCRIPT_PLACEHOLDER to input.transcriptText,
                    COMBINED_TRANSCRIPTS_PLACEHOLDER to (input.combinedTranscriptText ?: input.transcriptText),
                    TEXT_PLACEHOLDER to input.currentText,
                    CURRENT_TEXT_PLACEHOLDER to input.currentText,
                    PRIOR_OUTPUT_PLACEHOLDER to input.currentText,
                    SESSION_ID_PLACEHOLDER to input.sessionId,
                    TRANSCRIPT_ID_PLACEHOLDER to input.transcriptId,
                    PROFILE_ID_PLACEHOLDER to input.profileId,
                )
            return replacements.entries.fold(input.systemPrompt) { rendered, (placeholder, value) ->
                rendered.replace(placeholder, value)
            }
        }

        private fun containsTranscriptPlaceholder(systemPrompt: String): Boolean =
            systemPrompt.contains(TRANSCRIPT_PLACEHOLDER) ||
                systemPrompt.contains(COMBINED_TRANSCRIPTS_PLACEHOLDER) ||
                systemPrompt.contains(TEXT_PLACEHOLDER) ||
                systemPrompt.contains(CURRENT_TEXT_PLACEHOLDER) ||
                systemPrompt.contains(PRIOR_OUTPUT_PLACEHOLDER)

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

        private companion object {
            const val TRANSCRIPT_PLACEHOLDER = "{{transcript}}"
            const val COMBINED_TRANSCRIPTS_PLACEHOLDER = "{{combined_transcripts}}"
            const val TEXT_PLACEHOLDER = "{{text}}"
            const val CURRENT_TEXT_PLACEHOLDER = "{{current_text}}"
            const val PRIOR_OUTPUT_PLACEHOLDER = "{{prior_output}}"
            const val SESSION_ID_PLACEHOLDER = "{{session_id}}"
            const val TRANSCRIPT_ID_PLACEHOLDER = "{{transcript_id}}"
            const val PROFILE_ID_PLACEHOLDER = "{{profile_id}}"
            val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
