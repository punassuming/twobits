package dev.scrybe.core.transcription

import android.util.Log
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.SpeakerSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
        private val endpointResolver: OpenAiEndpointResolver,
        private val debugStore: DiarizationDebugStore,
        private val aiCallDebugStore: AiCallDebugStore,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) : DiarizationService {
        override suspend fun diarize(
            sessionId: String,
            audioFile: File,
            transcriptText: String,
            providerType: ProviderType,
        ): Result<List<SpeakerSegment>> =
            runCatching {
                withContext(Dispatchers.IO) {
                    val debugEnabled = preferencesDataStore.debugDiarization.first()
                    val endpoint = endpointResolver.resolve()

                    val verboseSegments = transcribeVerbose(audioFile, endpoint, debugEnabled)
                    Log.d(
                        TAG,
                        "verbose transcription: ${verboseSegments.size} segments, " +
                            "${verboseSegments.count { !it.words.isNullOrEmpty() }} with word timestamps",
                    )
                    if (verboseSegments.isEmpty()) return@withContext emptyList()

                    val llmRun = assignSpeakers(verboseSegments, endpoint, debugEnabled)

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
                    if (debugEnabled) {
                        debugStore.write(buildDebugInfo(sessionId, verboseSegments, llmRun, merged.size))
                    }
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

        private suspend fun transcribeVerbose(
            audioFile: File,
            endpoint: OpenAiEndpoint,
            debugEnabled: Boolean,
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
                    .url("${endpoint.baseUrl}/v1/audio/transcriptions")
                    .header("Authorization", "Bearer ${endpoint.authToken}")
                    .header("X-TwoBits-App", "scrybe")
                    .header("X-TwoBits-Op", "diarize-audio")
                    .post(requestBody)
                    .build()

            // Recorded here (not just in the outer diarize() runCatching) because a failure at this
            // step never reaches DiarizationDebugStore.write() below — that only fires after this
            // call already succeeded, so without this, the single most common failure point would
            // be invisible in the per-session debug card with nothing indicating why. `recorded`
            // guards against double-recording: it's set the moment any branch below writes an
            // entry, so the outer catch (network failure, timeout — anything before/after a
            // response) only records if nothing more specific already did.
            var recorded = false

            suspend fun recordOnce(
                success: Boolean,
                httpStatus: Int?,
                snippet: String,
            ) {
                if (!debugEnabled || recorded) return
                recorded = true
                aiCallDebugStore.record(
                    AiCallDebugEntry(
                        timestampMs = System.currentTimeMillis(),
                        op = "diarize-audio",
                        endpoint = "/v1/audio/transcriptions",
                        model = "whisper-1",
                        requestSummary = "verbose_json, file=${audioFile.name}",
                        success = success,
                        httpStatus = httpStatus,
                        responseSnippet = snippet,
                    ),
                )
            }

            return runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val err =
                            response.body
                                ?.string()
                                .orEmpty()
                                .take(300)
                        recordOnce(success = false, httpStatus = response.code, snippet = err)
                        throw IOException("Whisper verbose_json failed: ${response.code} $err")
                    }
                    val body = response.body?.string() ?: throw IOException("Empty response")
                    val segments = json.decodeFromString<VerboseTranscriptionResponse>(body).segments ?: emptyList()
                    recordOnce(success = true, httpStatus = response.code, snippet = "${segments.size} segments")
                    segments
                }
            }.getOrElse { error ->
                recordOnce(success = false, httpStatus = null, snippet = "${error.javaClass.simpleName}: ${error.message}")
                throw error
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

        private fun buildDiarizationPrompt(segmentsJson: String): String =
            """
            You are a speaker diarization expert. Assign a speaker ID to each transcript segment.

            Rules:
            - Use SPEAKER_1, SPEAKER_2, etc. A speaker who reappears MUST reuse their original ID.
            - Default to the fewest speakers that explain the data (usually 2).
            - A gap ≥ ${MIN_SPEAKER_CHANGE_GAP_SECONDS}s between segments is a STRONG signal of a speaker change, but is not required.
            - You MAY change speakers within shorter gaps when the text strongly indicates a turn:
                * A direct question followed by a direct answer
                * A discourse marker that typically opens a reply ("Right,", "Exactly,", "So basically,", "I think,")
                * A clear contradiction or agreement with the prior speaker's statement
            - You must NOT change speakers mid-sentence or mid-phrase (within a continuous word run with no gap).
            - If a segment is ≤ 1 word and surrounded by the same speaker, assign it to that speaker.
            - Return ONLY a JSON array: [{"index":0,"speakerId":"SPEAKER_1"},...]

            Transcript segments (JSON):
            $segmentsJson
            """.trimIndent()

        private suspend fun callDiarizationLlm(
            prompt: String,
            endpoint: OpenAiEndpoint,
            debugEnabled: Boolean,
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
                    maxOutputTokens = 3000,
                )
            val request =
                Request
                    .Builder()
                    .url("${endpoint.baseUrl}/v1/responses")
                    .header("Authorization", "Bearer ${endpoint.authToken}")
                    .header("X-TwoBits-App", "scrybe")
                    .header("X-TwoBits-Op", "diarize-assign")
                    .header("Content-Type", "application/json")
                    .post(
                        json
                            .encodeToString(OpenAiResponseRequest.serializer(), requestPayload)
                            .toRequestBody(JSON_MEDIA_TYPE),
                    ).build()

            // A non-2xx response or an unparseable body are treated as a soft "no assignment"
            // outcome (null -> parseAssignments() defaults everyone to SPEAKER_1) — this always
            // has been callDiarizationLlm()'s contract. A genuine transport failure (timeout, no
            // connectivity, TLS, etc.) is NOT part of that contract: it must still fail the whole
            // diarize() run via the outer runCatching, exactly as it did before this file gained
            // debug recording, rather than being silently folded into the same "everyone is
            // SPEAKER_1" fallback. So only the response-level record() calls below return null;
            // the transport-failure catch records for visibility and rethrows.
            suspend fun record(
                success: Boolean,
                httpStatus: Int?,
                snippet: String,
            ) {
                if (!debugEnabled) return
                aiCallDebugStore.record(
                    AiCallDebugEntry(
                        timestampMs = System.currentTimeMillis(),
                        op = "diarize-assign",
                        endpoint = "/v1/responses",
                        model = MODEL,
                        requestSummary = "prompt ${prompt.length} chars",
                        success = success,
                        httpStatus = httpStatus,
                        responseSnippet = snippet,
                    ),
                )
            }

            val response =
                try {
                    okHttpClient.newCall(request).execute()
                } catch (e: IOException) {
                    record(success = false, httpStatus = null, snippet = "${e.javaClass.simpleName}: ${e.message}")
                    throw e
                }
            return response.use {
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty().take(300)
                    record(success = false, httpStatus = response.code, snippet = err)
                    return@use null
                }
                val body = response.body?.string()
                val parsed = body?.let { runCatching { json.decodeFromString<OpenAiResponseResponse>(it) }.getOrNull() }
                val text =
                    parsed?.outputText
                        ?: parsed
                            ?.output
                            .orEmpty()
                            .flatMap { it.content.orEmpty() }
                            .filter { it.type == "output_text" }
                            .joinToString("") { it.text.orEmpty() }
                            .takeIf { parsed != null }
                record(success = text != null, httpStatus = response.code, snippet = text?.take(200) ?: "empty or unparseable response")
                text
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

        private suspend fun assignSpeakers(
            segments: List<VerboseSegment>,
            endpoint: OpenAiEndpoint,
            debugEnabled: Boolean,
        ): DiarizationLlmRun {
            val prompt = buildDiarizationPrompt(buildSegmentsJson(segments))
            if (debugEnabled) Log.d(TAG, "LLM prompt (${prompt.length} chars): ${prompt.take(500)}")
            val outputText = callDiarizationLlm(prompt, endpoint, debugEnabled)
            if (debugEnabled) Log.d(TAG, "LLM response: ${outputText?.take(500) ?: "<null>"}")
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
