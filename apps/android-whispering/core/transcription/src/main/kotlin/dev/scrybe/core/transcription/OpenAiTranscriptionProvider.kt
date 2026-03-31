package dev.scrybe.core.transcription

import android.util.Log
import dev.scrybe.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class OpenAiTranscriptionProvider
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val apiKeyProvider: ApiKeyProvider,
        private val audioChunker: OpenAiAudioChunker,
    ) : TranscriptionProvider {
        override val providerType: ProviderType = ProviderType.OPENAI

        override suspend fun transcribe(
            audioFile: File,
            options: TranscriptionOptions,
        ): Result<TranscriptResult> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val apiKey =
                        apiKeyProvider.getApiKey(ProviderType.OPENAI)
                            ?: throw IllegalStateException("No API key configured for OpenAI")

                    val audioChunks = audioChunker.createChunksIfNeeded(audioFile)
                    try {
                        val transcriptText =
                            audioChunks.joinToString(separator = "\n\n") { chunk ->
                                transcribeChunk(
                                    audioFile = chunk,
                                    apiKey = apiKey,
                                    options = options,
                                )
                            }

                        TranscriptResult(
                            text = transcriptText.trim(),
                            language = null,
                            durationSeconds = null,
                        )
                    } finally {
                        audioChunker.cleanupChunks(audioChunks, audioFile)
                    }
                }
            }

        private fun transcribeChunk(
            audioFile: File,
            apiKey: String,
            options: TranscriptionOptions,
        ): String {
            val mediaType = audioFile.toMediaType()
            val requestBody =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", "whisper-1")
                    .addFormDataPart(
                        "file",
                        audioFile.name,
                        audioFile.asRequestBody(mediaType.toMediaType()),
                    )
                    .apply {
                        options.language?.let { addFormDataPart("language", it) }
                        options.prompt?.let { addFormDataPart("prompt", it) }
                        addFormDataPart("response_format", options.responseFormat)
                    }
                    .build()

            val request =
                Request.Builder()
                    .url("https://api.openai.com/v1/audio/transcriptions")
                    .header("Authorization", "Bearer $apiKey")
                    .header("Accept", "application/json")
                    .post(requestBody)
                    .build()

            return okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty().replace("\n", " ").take(500)
                    Log.e(TAG, "OpenAI transcription failed: ${response.code} ${response.message} $errorBody")
                    throw IllegalStateException(
                        "OpenAI API error: ${response.code} ${response.message}" +
                            if (errorBody.isNotBlank()) " - $errorBody" else "",
                    )
                }

                val body = response.body?.string() ?: throw IllegalStateException("Empty response body")
                json.decodeFromString<OpenAiTranscriptionResponse>(body).text
            }
        }

        @Serializable
        private data class OpenAiTranscriptionResponse(val text: String)

        private fun File.toMediaType(): String =
            when (extension.lowercase()) {
                "m4a", "mp4" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                "webm" -> "audio/webm"
                "aac" -> "audio/aac"
                else -> "application/octet-stream"
            }

        private companion object {
            const val TAG = "OpenAiTranscription"
        }
    }
