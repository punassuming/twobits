package dev.scrybe.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.transcription.BatchTranscriptionTracker
import dev.scrybe.core.transcription.TranscriptionCancellationController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 *
 * That signal alone undercounts a "transcribe selected" batch, though:
 * `HistoryViewModel.transcribeSelectedSessions()` processes one session at a time, so at most one
 * ever reports TRANSCRIBING — the rest of the batch is otherwise indistinguishable from "nothing
 * else pending." [BatchTranscriptionTracker] fills that specific gap; combined with the DB
 * signal, [queuedCount] covers both a same-batch backlog and any other transcription that happens
 * to be running concurrently (e.g. auto-transcribe firing while a manual retry is in flight).
 */
@HiltViewModel
class TranscriptionProgressViewModel
    @Inject
    constructor(
        recordingSessionDao: RecordingSessionDao,
        batchTranscriptionTracker: BatchTranscriptionTracker,
        private val cancellationController: TranscriptionCancellationController,
    ) : ViewModel() {
        val uiState: StateFlow<TranscriptionProgressUiState> =
            combine(
                recordingSessionDao.observeSessionsByStatus(SessionStatus.TRANSCRIBING.name),
                batchTranscriptionTracker.remaining,
            ) { sessions, batchRemaining ->
                val current = sessions.firstOrNull()
                TranscriptionProgressUiState(
                    isTranscribing = current != null || batchRemaining > 0,
                    label = current?.title.orEmpty(),
                    queuedCount = (sessions.size - 1).coerceAtLeast(0) + batchRemaining,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TranscriptionProgressUiState(),
            )

        /** Stops whatever transcription(s) are currently in flight or queued behind them. */
        fun cancel() {
            cancellationController.cancelAll()
        }
    }
