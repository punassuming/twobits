package dev.scrybe.core.transcription.realtime

import kotlinx.coroutines.flow.Flow

/** A transcript event streamed from an open [RealtimeTranscriptSession]. */
sealed interface TranscriptEvent {
    /** Growing transcript text for the segment currently being spoken. */
    data class Delta(
        val textSoFar: String,
    ) : TranscriptEvent

    /** A segment finished transcribing; [finalText] is that segment's final text. */
    data class Completed(
        val finalText: String,
    ) : TranscriptEvent

    /** The connection failed or was closed by the server; no more events will follow. */
    data class Failed(
        val reason: String,
    ) : TranscriptEvent
}

/**
 * An open streaming-transcription connection. Implementations own the WebSocket lifecycle;
 * callers append raw PCM16 audio as it's captured and collect [events] for incremental text.
 */
interface RealtimeTranscriptSession {
    val events: Flow<TranscriptEvent>

    suspend fun appendAudio(pcm16: ByteArray)

    /** Closes the connection and returns the best-effort accumulated transcript, if any. */
    suspend fun close(): Result<String>
}
