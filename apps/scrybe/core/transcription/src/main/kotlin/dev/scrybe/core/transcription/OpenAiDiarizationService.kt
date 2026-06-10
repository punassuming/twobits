package dev.scrybe.core.transcription

import android.util.Log
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
        private val debugStore: DiarizationDebugStore,
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
                    Log.d(
                        TAG,
                        "verbose transcription: ${verboseSegments.size} segments, " +
                            "${verboseSegments.count { !it.words.isNullOrEmpty() }} with word timestamps",
                    )
                    if (verboseSegments.isEmpty()) return@withContext emptyList()

                    val llmRun = assignSpeakers(verboseSegments, apiKey)

                    val rawSegments =
                        verboseSegments.mapIndexed { index, segment ->
                            val speakerId = llmRun.assignments.getOrNull(index) ?: "SPEAKER_1"
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
                    val merged = mergeAdjacentSegments(rawSegments)
                    Log.d(TAG, "merged ${rawSegments.size} raw segments into ${merged.size}")
                    debugStore.write(
                        buildDebugInfo(sessionId, verboseSegments, llmRun, merged.size),
                    )
                    merged
                }
            }

        private fun buildDebugInfo(
            sessionId: String,
            verboseSegments: List<VerboseSegment>,
            llmRun: DiarizationLlmRun,
            mergedSegmentCount: Int,
        ): DiarizationDebugInfo =
            DiarizationDebugInfo(
                sessionId = sessionId,
                runAtMs = System.currentTimeMillis(),
                model = MODEL,
                verboseSegmentCount = verboseSegments.size,
                wordTimestampsPresent = verboseSegments.any { !it.words.isNullOrEmpty() },
                prompt = llmRun.prompt,
                rawLlmResponse = llmRun.rawOutput,
                assignments = llmRun.assignments,
                mergedSegmentCount = mergedSegmentCount,
            )

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
                    .addFormDataPart("timestamp_granularities[]", "segment")
                    .addFormDataPart("timestamp_granularities[]", "word")
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

        private fun buildWordsJson(words: List<WordTimestamp>?): String =
            words?.joinToString(",", "[", "]") { w ->
                val ew = w.word.replace("\"", "\\\"")
                """{"word":"$ew","start":${w.start},"end":${w.end}}"""
            } ?: "[]"

        private fun buildSegmentsJson(segments: List<VerboseSegment>): String =
            segments
                .mapIndexed { i, s ->
                    val escapedText = s.text.replace("\"", "\\\"")
                    val wordsJson = buildWordsJson(s.words)
                    """{"index":$i,"start":${s.start},"end":${s.end},"text":"$escapedText","words":$wordsJson}"""
                }.joinToString(",", "[", "]")

        @Suppress("MaxLineLength")
        private fun buildDiarizationPrompt(segmentsJson: String): String =
            """
            You are a speaker diarization expert. Analyze the transcript segments below and assign a speaker ID to each.

            Rules:
            - Use SPEAKER_1, SPEAKER_2, etc. A speaker who reappears later MUST reuse their original ID.
            - Default to the fewest speakers that explain the data. Assume 2 speakers unless the evidence strongly indicates more.
            - Do NOT change speakers within a continuous word run. Only assign a new speaker when the gap between the last word of a segment and the first word of the next is at least $MIN_SPEAKER_CHANGE_GAP_SECONDS seconds.
            - If a segment is shorter than $MIN_SPEAKER_CHANGE_GAP_SECONDS seconds and surrounded by segments of the same speaker, assign it to that speaker regardless of content.
            - Return ONLY a JSON array with no extra text: [{"index":0,"speakerId":"SPEAKER_1"},{"index":1,"speakerId":"SPEAKER_2"},...]

            Transcript segments (JSON):
            $segmentsJson
            """.trimIndent()

        private fun callDiarizationLlm(
            prompt: String,
            apiKey: String,
        ): String? {
            val requestPayload =
                OpenAiResponseRequest(
                    model = MODEL,
                    instructions = "You are a speaker diarization assistant.",
                    input =
                        listOf(
                            ResponseInputMessage(
                                type = "message",
                                role = "user",
                                content = listOf(InputText(type = "input_text", text = prompt)),
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
                            .encodeToString(OpenAiResponseRequest.serializer(), requestPayload)
                            .toRequestBody(JSON_MEDIA_TYPE),
                    ).build()
            return okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val parsed =
                    runCatching { json.decodeFromString<OpenAiResponseResponse>(body) }.getOrNull()
                        ?: return@use null
                parsed.outputText
                    ?: parsed.output
                        .orEmpty()
                        .flatMap { it.content.orEmpty() }
                        .filter { it.type == "output_text" }
                        .joinToString("") { it.text.orEmpty() }
            }
        }

        private fun parseAssignments(
            outputText: String?,
            size: Int,
        ): List<String> {
            if (outputText == null) return List(size) { "SPEAKER_1" }
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
            }.getOrElse { List(size) { "SPEAKER_1" } }
        }

        private fun assignSpeakers(
            segments: List<VerboseSegment>,
            apiKey: String,
        ): DiarizationLlmRun {
            val prompt = buildDiarizationPrompt(buildSegmentsJson(segments))
            Log.d(TAG, "LLM prompt (${prompt.length} chars): ${prompt.take(500)}")
            val outputText = callDiarizationLlm(prompt, apiKey)
            Log.d(TAG, "LLM response: ${outputText?.take(500) ?: "<null>"}")
            val assignments = parseAssignments(outputText, segments.size)
            Log.d(TAG, "assignments: ${assignments.groupingBy { it }.eachCount()}")
            return DiarizationLlmRun(prompt = prompt, rawOutput = outputText, assignments = assignments)
        }

        /** The full LLM exchange for one diarization run, retained for the debug record. */
        private data class DiarizationLlmRun(
            val prompt: String,
            val rawOutput: String?,
            val assignments: List<String>,
        )

        private fun mergeAdjacentSegments(segments: List<SpeakerSegment>): List<SpeakerSegment> {
            if (segments.isEmpty()) return emptyList()
            val merged = mutableListOf<SpeakerSegment>()
            var current = segments.first()
            for (next in segments.drop(1)) {
                current =
                    if (next.speakerId == current.speakerId) {
                        current.copy(endMs = next.endMs)
                    } else {
                        merged.add(current)
                        next
                    }
            }
            merged.add(current)
            return merged
        }

        @Serializable
        private data class VerboseTranscriptionResponse(
            val segments: List<VerboseSegment>? = null,
        )

        @Serializable
        private data class WordTimestamp(
            val word: String,
            val start: Double,
            val end: Double,
        )

        @Serializable
        private data class VerboseSegment(
            val id: Int = 0,
            val start: Double,
            val end: Double,
            val text: String,
            val words: List<WordTimestamp>? = null,
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
            const val TAG = "Diarization"
            const val MODEL = "gpt-5-mini"

            /** Minimum gap between the last word of one segment and the first word of the next
             *  before the model is allowed to assign a different speaker. Below this threshold
             *  the two segments are treated as a continuous utterance from a single speaker. */
            const val MIN_SPEAKER_CHANGE_GAP_SECONDS = 0.8

            val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
