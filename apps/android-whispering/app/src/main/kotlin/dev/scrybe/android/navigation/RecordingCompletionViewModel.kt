package dev.scrybe.android.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.PostStopDestination
import dev.scrybe.service.recording.RecordingSessionEvents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RecordingCompletionNavEvent {
    data object OpenHome : RecordingCompletionNavEvent

    data class OpenSessionReview(val sessionId: String) : RecordingCompletionNavEvent
}

@HiltViewModel
class RecordingCompletionViewModel
    @Inject
    constructor(
        recordingSessionEvents: RecordingSessionEvents,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) : ViewModel() {
        private val _events = MutableSharedFlow<RecordingCompletionNavEvent>()
        val events = _events.asSharedFlow()

        init {
            viewModelScope.launch {
                recordingSessionEvents.completedSessions.collect { sessionId ->
                    val destination = preferencesDataStore.postStopDestination.first()
                    _events.emit(
                        when (destination) {
                            PostStopDestination.HOME -> RecordingCompletionNavEvent.OpenHome
                            PostStopDestination.SESSION_REVIEW -> RecordingCompletionNavEvent.OpenSessionReview(sessionId)
                        },
                    )
                }
            }
        }
    }
