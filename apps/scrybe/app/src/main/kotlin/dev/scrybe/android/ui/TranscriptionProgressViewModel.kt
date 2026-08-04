package dev.scrybe.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.model.SessionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TranscriptionProgressUiState(
    val isTranscribing: Boolean = false,
    val label: String = "",
    val queuedCount: Int = 0,
)

/**
 * Backed by [RecordingSessionEntity.status][dev.scrybe.core.database.RecordingSessionEntity],
 * not a dedicated event bus: [dev.scrybe.core.transcription.SessionTranscriptionCoordinator]
 * already flips a session to [SessionStatus.TRANSCRIBING] for the duration of every transcribe
 * call (auto or manual — retry, batch, or the session-detail button all funnel through it), so
 * this reuses that existing, already-reactive signal instead of adding a parallel one.
 */
@HiltViewModel
class TranscriptionProgressViewModel
    @Inject
    constructor(
        recordingSessionDao: RecordingSessionDao,
    ) : ViewModel() {
        val uiState: StateFlow<TranscriptionProgressUiState> =
            recordingSessionDao
                .observeSessionsByStatus(SessionStatus.TRANSCRIBING.name)
                .map { sessions ->
                    val current = sessions.firstOrNull()
                    TranscriptionProgressUiState(
                        isTranscribing = current != null,
                        label = current?.title.orEmpty(),
                        queuedCount = (sessions.size - 1).coerceAtLeast(0),
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = TranscriptionProgressUiState(),
                )
    }
