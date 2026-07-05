package dev.scrybe.service.recording

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A realtime-streaming-transcription status update for the recording currently in progress. */
sealed interface LiveStreamEvent {
    data class Connecting(
        val correlationId: String,
    ) : LiveStreamEvent

    data class Delta(
        val correlationId: String,
        val textSoFar: String,
    ) : LiveStreamEvent

    /** Streaming never started for this recording (Local/OFF tier, mic-capture failure, etc.). */
    data class Unavailable(
        val correlationId: String,
        val reason: String,
    ) : LiveStreamEvent

    /** Streaming was active but the connection dropped mid-recording. */
    data class Dropped(
        val correlationId: String,
    ) : LiveStreamEvent
}

@Singleton
class RecordingSessionEvents
    @Inject
    constructor() {
        private val _completedSessions = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val completedSessions: SharedFlow<String> = _completedSessions.asSharedFlow()
        private val _recordingErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val recordingErrors: SharedFlow<String> = _recordingErrors.asSharedFlow()
        private val _liveStreamEvents = MutableSharedFlow<LiveStreamEvent>(extraBufferCapacity = 8)
        val liveStreamEvents: SharedFlow<LiveStreamEvent> = _liveStreamEvents.asSharedFlow()

        fun onSessionCompleted(sessionId: String) {
            _completedSessions.tryEmit(sessionId)
        }

        fun onRecordingError(message: String) {
            _recordingErrors.tryEmit(message)
        }

        fun onLiveStreamEvent(event: LiveStreamEvent) {
            _liveStreamEvents.tryEmit(event)
        }
    }
