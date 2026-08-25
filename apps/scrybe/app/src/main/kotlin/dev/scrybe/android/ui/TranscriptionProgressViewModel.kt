package dev.scrybe.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.transcription.BatchTranscriptionTracker
import dev.scrybe.core.transcription.TranscriptionCancellationController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TranscriptionProgressUiState(
    val isTranscribing: Boolean = false,
    val label: String = "",
    val queuedCount: Int = 0,
    val isCancelling: Boolean = false,
)

/**
 * Backed by [RecordingSessionEntity.status][dev.scrybe.core.database.RecordingSessionEntity],
 * not a dedicated event bus: [dev.scrybe.core.transcription.SessionTranscriptionCoordinator]
 * already flips a session to [SessionStatus.TRANSCRIBING] for the duration of every transcribe
 * call (auto or manual — retry, batch, or the session-detail button all funnel through it), so
 * this reuses that existing, already-reactive signal instead of adding a parallel one.
 *
 * That signal alone undercounts a "transcribe selected" batch, though: such a batch processes
 * one session at a time, so at most one ever reports TRANSCRIBING — the rest of the batch is
 * otherwise indistinguishable from "nothing else pending." [BatchTranscriptionTracker] fills
 * that specific gap; combined with the DB signal, [queuedCount] covers both a same-batch
 * backlog and any other transcription that happens to be running concurrently (e.g.
 * auto-transcribe firing while a manual retry is in flight).
 */
@HiltViewModel
class TranscriptionProgressViewModel
    @Inject
    constructor(
        recordingSessionDao: RecordingSessionDao,
        batchTranscriptionTracker: BatchTranscriptionTracker,
        private val cancellationController: TranscriptionCancellationController,
    ) : ViewModel() {
        // Purely a local "did the tap register" signal — there's no DB/coordinator state for
        // "a cancel was requested but the in-flight native decode hasn't noticed yet" to derive
        // this from (see WhisperEngine's chunk-boundary-only cancellation checkpoints), so it's
        // owned here rather than added as a new SessionStatus.
        private val cancellingFlow = MutableStateFlow(false)
        private var cancellingTimeoutJob: Job? = null

        /** DB-derived progress only — deliberately excludes [cancellingFlow] so nothing here loops back into it. */
        private val transcribingState: StateFlow<TranscriptionProgressUiState> =
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

        val uiState: StateFlow<TranscriptionProgressUiState> =
            combine(transcribingState, cancellingFlow) { state, isCancelling ->
                state.copy(isCancelling = isCancelling && state.isTranscribing)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TranscriptionProgressUiState(),
            )

        init {
            // Plain side-effecting observer of the DB-derived state, not folded into a combine
            // transform that also reads cancellingFlow — mutating a StateFlow from inside its own
            // combine() would feed back into that same combine, which works but is needlessly
            // fragile to reason about. This way the mutation only ever reacts to genuinely
            // upstream state.
            viewModelScope.launch {
                transcribingState.collect { state ->
                    if (!state.isTranscribing && cancellingFlow.value) {
                        cancellingFlow.value = false
                    }
                }
            }
        }

        /** Stops whatever transcription(s) are currently in flight or queued behind them. */
        fun cancel() {
            cancellingFlow.value = true
            cancellationController.cancelAll()
            // Safety net: WhisperEngine's native decode has no true interruption hook, so
            // cancellation is only guaranteed to be noticed at a chunk boundary — if that somehow
            // never arrives, this stops "Cancelling…" from being stuck on the toast forever.
            cancellingTimeoutJob?.cancel()
            cancellingTimeoutJob =
                viewModelScope.launch {
                    delay(15_000)
                    cancellingFlow.value = false
                }
        }
    }
