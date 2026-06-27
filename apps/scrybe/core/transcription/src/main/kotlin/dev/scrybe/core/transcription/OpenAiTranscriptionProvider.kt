package dev.scrybe.core.transcription

import android.util.Log
import dev.scrybe.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import javax.inject.Inject

class OpenAiTranscriptionProvider
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val endpointResolver: OpenAiEndpointResolver,
        private val audioChunker: OpenAiAudioChunker,
    ) : TranscriptionProvider {
        override val providerType: ProviderType = ProviderType.OPENAI

        override suspend fun transcribe(
            audioFile: File,
            options: TranscriptionOptions,
        ): Result<TranscriptResult> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val endpoint = endpointResolver.resolve()

                    val audioChunks = audioChunker.createChunksIfNeeded(audioFile)
                    try {
                        val parts = mutableListOf<String>()
                        for (chunk in audioChunks) {
                            parts.add(transcribeChunk(audioFile = chunk, endpoint = endpoint, options = options))
                        }
                        val transcriptText = parts.joinToString(separator = "\n\n")

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

        private suspend fun transcribeChunk(
            audioFile: File,
            endpoint: OpenAiEndpoint,
            options: TranscriptionOptions,
        ): String {
            var lastError: Exception = IllegalStateException("No attempts made")
            for (attempt in 0 until MAX_CHUNK_ATTEMPTS) {
                try {
                    return doTranscribeChunk(audioFile, endpoint, options)
                } catch (e: ChunkApiException) {
                    lastError = e
                    if (!e.isRetriable || attempt == MAX_CHUNK_ATTEMPTS - 1) throw e
                } catch (e: IOException) {
                    lastError = e
                    if (attempt == MAX_CHUNK_ATTEMPTS - 1) throw e
                }
                val delayMs = RETRY_BASE_DELAY_MS shl attempt
                Log.w(TAG, "Chunk attempt ${attempt + 1}/$MAX_CHUNK_ATTEMPTS failed; retrying in ${delayMs}ms", lastError)
                delay(delayMs)
            }
            throw lastError
        }

        private fun doTranscribeChunk(
            audioFile: File,
            endpoint: OpenAiEndpoint,
            options: TranscriptionOptions,
        ): String {
            val mediaType = audioFile.toMediaType()
            val requestBody =
                MultipartBody
                    .Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", options.model)
                    .addFormDataPart(
                        "file",
                        audioFile.name,
                        audioFile.asRequestBody(mediaType.toMediaType()),
                    ).apply {
                        options.language?.let { addFormDataPart("language", it) }
                        options.prompt?.let { addFormDataPart("prompt", it) }
                        addFormDataPart("response_format", options.responseFormat)
                    }.build()

            val request =
                Request
                    .Builder()
                    .url("${endpoint.baseUrl}/v1/audio/transcriptions")
                    .header("Authorization", "Bearer ${endpoint.authToken}")
                    .header("X-TwoBits-App", "scrybe")
                    .header("X-TwoBits-Op", "transcribe")
                    .header("Accept", "application/json")
                    .post(requestBody)
                    .build()

            return okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody =
                        response.body
                            ?.string()
                            .orEmpty()
                            .replace("\n", " ")
                            .take(500)
                    Log.e(TAG, "OpenAI transcription failed: ${response.code} ${response.message} $errorBody")
                    throw ChunkApiException(
                        code = response.code,
                        message =
                            "OpenAI API error: ${response.code} ${response.message}" +
                                if (errorBody.isNotBlank()) " - $errorBody" else "",
                    )
                }
                val body = response.body?.string() ?: throw IllegalStateException("Empty response body")
                json.decodeFromString<OpenAiTranscriptionResponse>(body).text
            }
        }

        private class ChunkApiException(
            val code: Int,
            message: String,
        ) : IllegalStateException(message) {
            val isRetriable: Boolean get() = code == 429 || code >= 500
        }

        @Serializable
        private data class OpenAiTranscriptionResponse(
            val text: String,
        )

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
            const val MAX_CHUNK_ATTEMPTS = 3
            const val RETRY_BASE_DELAY_MS = 2_000L
        }
    }
