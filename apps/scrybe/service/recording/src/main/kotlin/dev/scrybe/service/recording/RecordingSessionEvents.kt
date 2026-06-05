package dev.scrybe.service.recording

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingSessionEvents
    @Inject
    constructor() {
        private val _completedSessions = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val completedSessions: SharedFlow<String> = _completedSessions.asSharedFlow()
        private val _recordingErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val recordingErrors: SharedFlow<String> = _recordingErrors.asSharedFlow()
        private val _liveTranscriptUpdates = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 8)
        val liveTranscriptUpdates: SharedFlow<String> = _liveTranscriptUpdates.asSharedFlow()

        fun onSessionCompleted(sessionId: String) {
            _completedSessions.tryEmit(sessionId)
        }

        fun onRecordingError(message: String) {
            _recordingErrors.tryEmit(message)
        }

        fun onLiveTranscriptUpdate(text: String) {
            _liveTranscriptUpdates.tryEmit(text)
        }
    }
