package dev.scrybe.core.transcription

import dev.scrybe.core.model.ProviderType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class OpenAiTranscriptionProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val apiKeyProvider: ApiKeyProvider,
) : TranscriptionProvider {

    override val providerType: ProviderType = ProviderType.OPENAI

    override suspend fun transcribe(audioFile: File, options: TranscriptionOptions): Result<TranscriptResult> =
        runCatching {
            val apiKey = apiKeyProvider.getApiKey(ProviderType.OPENAI)
                ?: throw IllegalStateException("No API key configured for OpenAI")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/m4a".toMediaType())
                )
                .apply {
                    options.language?.let { addFormDataPart("language", it) }
                    options.prompt?.let { addFormDataPart("prompt", it) }
                    addFormDataPart("response_format", options.responseFormat)
                }
                .build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("OpenAI API error: ${response.code} ${response.message}")
            }

            val body = response.body?.string() ?: throw Exception("Empty response body")
            val openAiResponse = json.decodeFromString<OpenAiTranscriptionResponse>(body)
            TranscriptResult(
                text = openAiResponse.text,
                language = null,
                durationSeconds = null,
            )
        }

    @Serializable
    private data class OpenAiTranscriptionResponse(val text: String)
}
