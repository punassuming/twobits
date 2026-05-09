package dev.scrybe.core.transcription

import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.SpeakerSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

@Singleton
class OpenAiDiarizationService
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val apiKeyProvider: ApiKeyProvider,
    ) : DiarizationService {
        override suspend fun diarize(
            sessionId: String,
            audioFile: File,
            transcriptText: String,
            providerType: ProviderType,
        ): Result<List<SpeakerSegment>> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val apiKey =
                        apiKeyProvider.getApiKey(ProviderType.OPENAI)
                            ?: throw IllegalStateException("No API key configured for OpenAI")

                    val verboseSegments = transcribeVerbose(audioFile, apiKey)
                    if (verboseSegments.isEmpty()) return@withContext emptyList()

                    val speakerAssignments = assignSpeakers(verboseSegments, apiKey)

                    verboseSegments.mapIndexed { index, segment ->
                        val speakerId = speakerAssignments.getOrNull(index) ?: "SPEAKER_1"
                        SpeakerSegment(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            speakerId = speakerId,
                            speakerLabel = null,
                            personId = null,
                            startMs = (segment.start * 1000.0).roundToLong(),
                            endMs = (segment.end * 1000.0).roundToLong(),
                        )
                    }
                }
            }

        private fun transcribeVerbose(
            audioFile: File,
            apiKey: String,
        ): List<VerboseSegment> {
            val mediaType =
                when (audioFile.extension.lowercase()) {
                    "m4a", "mp4" -> "audio/mp4"
                    "mp3" -> "audio/mpeg"
                    "wav" -> "audio/wav"
                    "ogg" -> "audio/ogg"
                    "webm" -> "audio/webm"
                    else -> "audio/aac"
                }
            val requestBody =
                MultipartBody
                    .Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", "whisper-1")
                    .addFormDataPart("response_format", "verbose_json")
                    .addFormDataPart("file", audioFile.name, audioFile.asRequestBody(mediaType.toMediaType()))
                    .build()

            val request =
                Request
                    .Builder()
                    .url("https://api.openai.com/v1/audio/transcriptions")
                    .header("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

            return okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err =
                        response.body
                            ?.string()
                            .orEmpty()
                            .take(300)
                    throw IOException("Whisper verbose_json failed: ${response.code} $err")
                }
                val body = response.body?.string() ?: throw IOException("Empty response")
                json.decodeFromString<VerboseTranscriptionResponse>(body).segments ?: emptyList()
            }
        }

        private fun assignSpeakers(
            segments: List<VerboseSegment>,
            apiKey: String,
        ): List<String> {
            val segmentsJson =
                segments
                    .mapIndexed { i, s ->
                        """{"index":$i,"start":${s.start},"end":${s.end},"text":"${s.text.replace("\"", "\\\"")}"}"""
                    }.joinToString(",", "[", "]")

            val userMessage =
                """
                Transcript segments (JSON):
                $segmentsJson

                Assign each segment a speaker ID. Use SPEAKER_1, SPEAKER_2, etc. consistently for the same speaker.
                Return only JSON: [{"index":0,"speakerId":"SPEAKER_1"},{"index":1,"speakerId":"SPEAKER_2"},...]
                """.trimIndent()

            val requestPayload =
                OpenAiResponseRequest(
                    model = MODEL,
                    instructions = "You are a speaker diarization assistant. Identify speaker turns from transcript segments.",
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
                    .post(json.encodeToString(OpenAiResponseRequest.serializer(), requestPayload).toRequestBody(JSON_MEDIA_TYPE))
                    .build()

            val outputText =
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return List(segments.size) { "SPEAKER_1" }
                    val body = response.body?.string() ?: return List(segments.size) { "SPEAKER_1" }
                    val parsed =
                        runCatching { json.decodeFromString<OpenAiResponseResponse>(body) }.getOrNull()
                            ?: return List(segments.size) { "SPEAKER_1" }
                    parsed.outputText
                        ?: parsed.output
                            .orEmpty()
                            .flatMap { it.content.orEmpty() }
                            .filter { it.type == "output_text" }
                            .joinToString("") { it.text.orEmpty() }
                } ?: return List(segments.size) { "SPEAKER_1" }

            val cleaned =
                outputText
                    .trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

            return runCatching {
                json
                    .decodeFromString<List<SpeakerAssignment>>(cleaned)
                    .sortedBy { it.index }
                    .map { it.speakerId }
            }.getOrElse { List(segments.size) { "SPEAKER_1" } }
        }

        @Serializable
        private data class VerboseTranscriptionResponse(
            val segments: List<VerboseSegment>? = null,
        )

        @Serializable
        private data class VerboseSegment(
            val id: Int = 0,
            val start: Double,
            val end: Double,
            val text: String,
        )

        @Serializable
        private data class SpeakerAssignment(
            val index: Int,
            val speakerId: String,
        )

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
            const val MODEL = "gpt-5-mini"
            val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
