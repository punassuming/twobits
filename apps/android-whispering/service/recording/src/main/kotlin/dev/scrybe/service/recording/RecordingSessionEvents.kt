package dev.scrybe.service.recording

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class RecordingSessionEvents @Inject constructor() {
    private val _completedSessions = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val completedSessions: SharedFlow<String> = _completedSessions.asSharedFlow()

    fun onSessionCompleted(sessionId: String) {
        _completedSessions.tryEmit(sessionId)
    }
}
