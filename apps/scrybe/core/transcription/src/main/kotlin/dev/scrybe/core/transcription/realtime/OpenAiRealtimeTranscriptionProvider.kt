package dev.scrybe.core.transcription.realtime

import android.util.Base64
import android.util.Log
import dev.scrybe.core.transcription.OpenAiEndpoint
import dev.scrybe.core.transcription.OpenAiEndpointResolver
import dev.scrybe.core.transcription.PRESERVE_LANGUAGES_PROMPT
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens a streaming-transcription connection to OpenAI's Realtime API, routed through
 * [OpenAiEndpointResolver] exactly like the existing batch [dev.scrybe.core.transcription.OpenAiTranscriptionProvider] —
 * Pro connects through the TwoBits Worker relay, BYOK connects directly to OpenAI with the
 * user's own key, Worker never in the BYOK path.
 *
 * Deliberately not part of the [dev.scrybe.core.transcription.TranscriptionProvider] Dagger
 * multibinding map: that map exists because Local has a real alternate batch implementation;
 * Local has no streaming implementation at all (explicit non-goal), so this is injected directly
 * wherever needed and callers gate its use on `ExecutionMode != LOCAL && != OFF` themselves.
 *
 * Session-configuration schema: the Realtime API's Beta was fully retired on 2026-05-12, replacing
 * the old flat `session.{input_audio_format,input_audio_transcription,turn_detection}` shape with
 * a nested `session.audio.input.{format,transcription,turn_detection}` object (confirmed against
 * the current, non-beta `openai-python` SDK types — `openai.types.realtime.*`, not the deprecated
 * `openai.types.beta.realtime.*`). A live BYOK test against the old flat shape failed the
 * connection almost immediately after opening, consistent with the GA server rejecting it.
 */
@Singleton
class OpenAiRealtimeTranscriptionProvider
    @Inject
    constructor(
        private val okHttpClient: OkHttpClient,
        private val json: Json,
        private val endpointResolver: OpenAiEndpointResolver,
    ) {
        suspend fun open(model: String = DEFAULT_MODEL): Result<RealtimeTranscriptSession> {
            val endpoint = runCatching { endpointResolver.resolve() }.getOrElse { return Result.failure(it) }
            val request =
                Request
                    .Builder()
                    .url(realtimeUrl(endpoint))
                    .header("Authorization", "Bearer ${endpoint.authToken}")
                    .header("X-TwoBits-App", "scrybe")
                    .header("X-TwoBits-Op", "realtime-transcribe")
                    .build()
            val session = OpenAiRealtimeSession(json, model)
            session.connect(okHttpClient, request)
            return session.awaitHandshake().map { session }
        }

        private fun realtimeUrl(endpoint: OpenAiEndpoint): String {
            val wsBase = endpoint.baseUrl.replaceFirst("https://", "wss://")
            return if (endpoint.baseUrl == OpenAiEndpointResolver.OPENAI_BASE_URL) {
                "$wsBase/v1/realtime?intent=transcription"
            } else {
                "$wsBase/v1/audio/realtime"
            }
        }

        companion object {
            const val DEFAULT_MODEL = "gpt-4o-mini-transcribe"
        }
    }

private class OpenAiRealtimeSession(
    private val json: Json,
    private val model: String,
) : WebSocketListener(),
    RealtimeTranscriptSession {
    private val handshake = CompletableDeferred<Result<Unit>>()
    private val _events = MutableSharedFlow<TranscriptEvent>(extraBufferCapacity = 32)
    override val events: Flow<TranscriptEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null

    // Text from fully-completed speech segments (server VAD auto-segments on pauses), plus the
    // currently-in-progress segment's growing delta.
    private var completedText: String = ""
    private var currentSegmentText: String = ""

    // Once a Failed event has fired, close() must not report success with whatever partial text
    // happened to accumulate before the drop — the caller (RecordingForegroundService) relies on
    // close() failing here to fall back to the full post-stop batch transcription instead of
    // silently truncating the transcript at the point of the drop.
    private var failed = false

    fun connect(
        okHttpClient: OkHttpClient,
        request: Request,
    ) {
        webSocket = okHttpClient.newWebSocket(request, this)
    }

    suspend fun awaitHandshake(): Result<Unit> = handshake.await()

    override fun onOpen(
        webSocket: WebSocket,
        response: Response,
    ) {
        // Every field below is passed explicitly, even where the value never varies: the shared
        // Json instance (NetworkModule.providesNetworkJson()) doesn't set encodeDefaults = true,
        // so kotlinx.serialization silently drops any field left at its Kotlin default —
        // including the "type" discriminators the GA API needs to even recognize this as a
        // session.update event. These data classes have no defaults left for exactly that reason;
        // don't reintroduce one without also fixing serialization.
        val configMessage =
            json.encodeToString(
                SessionUpdateMessage.serializer(),
                SessionUpdateMessage(
                    type = "session.update",
                    session =
                        SessionConfig(
                            type = "transcription",
                            audio =
                                AudioConfig(
                                    input =
                                        AudioInputConfig(
                                            format = AudioFormatConfig(type = "audio/pcm", rate = 24_000),
                                            transcription =
                                                TranscriptionModelConfig(
                                                    model = model,
                                                    prompt = PRESERVE_LANGUAGES_PROMPT,
                                                ),
                                            turnDetection = TurnDetectionConfig(type = "server_vad"),
                                        ),
                                ),
                        ),
                ),
            )
        webSocket.send(configMessage)
        handshake.complete(Result.success(Unit))
    }

    override fun onMessage(
        webSocket: WebSocket,
        text: String,
    ) {
        val event = runCatching { json.decodeFromString(IncomingEvent.serializer(), text) }.getOrNull() ?: return
        when (event.type) {
            "conversation.item.input_audio_transcription.delta" -> {
                currentSegmentText += event.delta.orEmpty()
                _events.tryEmit(TranscriptEvent.Delta(fullTextSoFar()))
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val segmentText = event.transcript ?: currentSegmentText
                completedText = listOf(completedText, segmentText).filter { it.isNotBlank() }.joinToString(" ")
                currentSegmentText = ""
                _events.tryEmit(TranscriptEvent.Completed(completedText))
            }
            "conversation.item.input_audio_transcription.failed" -> {
                val message = event.error?.message ?: "Transcription failed for this segment"
                Log.w(TAG, "Realtime transcription failed event: $message")
                failed = true
                _events.tryEmit(TranscriptEvent.Failed(message))
            }
            "error" -> {
                val message = event.error?.message ?: "Realtime session error"
                Log.w(TAG, "Realtime session error event: $message")
                failed = true
                _events.tryEmit(TranscriptEvent.Failed(message))
            }
        }
    }

    override fun onFailure(
        webSocket: WebSocket,
        t: Throwable,
        response: Response?,
    ) {
        Log.w(TAG, "Realtime session connection failed", t)
        failed = true
        _events.tryEmit(TranscriptEvent.Failed(t.message ?: "Connection failed"))
        handshake.complete(Result.failure(t))
    }

    private fun fullTextSoFar(): String = listOf(completedText, currentSegmentText).filter { it.isNotBlank() }.joinToString(" ")

    override suspend fun appendAudio(pcm16: ByteArray) {
        val socket = webSocket ?: return
        val base64Audio = Base64.encodeToString(pcm16, Base64.NO_WRAP)
        socket.send(
            json.encodeToString(
                AudioAppendMessage.serializer(),
                AudioAppendMessage(type = "input_audio_buffer.append", audio = base64Audio),
            ),
        )
    }

    override suspend fun close(): Result<String> =
        runCatching {
            webSocket?.close(NORMAL_CLOSURE_CODE, "done")
            webSocket = null
            check(!failed) { "realtime session failed before it could be closed cleanly" }
            fullTextSoFar()
        }

    private companion object {
        const val TAG = "OpenAiRealtimeSession"
        const val NORMAL_CLOSURE_CODE = 1000
    }
}

// No default values below: the shared Json instance doesn't set encodeDefaults = true, so
// kotlinx.serialization silently omits any field left at its declared default — including "type"
// discriminators the GA API needs to recognize these events at all. Every field is required and
// passed explicitly at each (single) construction call site instead.
@Serializable
private data class SessionUpdateMessage(
    val type: String,
    val session: SessionConfig,
)

@Serializable
private data class SessionConfig(
    val type: String,
    val audio: AudioConfig,
)

@Serializable
private data class AudioConfig(
    val input: AudioInputConfig,
)

@Serializable
private data class AudioInputConfig(
    val format: AudioFormatConfig,
    val transcription: TranscriptionModelConfig,
    @SerialName("turn_detection") val turnDetection: TurnDetectionConfig,
)

@Serializable
private data class AudioFormatConfig(
    val type: String,
    val rate: Int,
)

@Serializable
private data class TranscriptionModelConfig(
    val model: String,
    val prompt: String,
)

@Serializable
private data class TurnDetectionConfig(
    val type: String,
)

@Serializable
private data class AudioAppendMessage(
    val type: String,
    val audio: String,
)

@Serializable
private data class IncomingEvent(
    val type: String,
    val delta: String? = null,
    val transcript: String? = null,
    val error: IncomingError? = null,
)

@Serializable
private data class IncomingError(
    val message: String? = null,
)
