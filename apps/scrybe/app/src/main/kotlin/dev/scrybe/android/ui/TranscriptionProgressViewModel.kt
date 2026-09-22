package dev.scrybe.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.transcription.BatchTranscriptionTracker
import dev.scrybe.core.transcription.TranscriptionCancellationController
import dev.scrybe.core.transcription.TranscriptionChunkProgressTracker
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
    /** How many of how many chunks are done for whichever transcription is currently running —
     * on-device or cloud, see [TranscriptionChunkProgressTracker]. Null when nothing is chunked
     * finely enough for a fraction to mean anything (a short clip, or nothing running at all). */
    val chunkProgress: TranscriptionChunkProgressTracker.Progress? = null,
)

/**
 * Liveness (`isTranscribing`/`queuedCount`) is driven by
 * [TranscriptionCancellationController.activeSessionIds], not by
 * [RecordingSessionEntity.status][dev.scrybe.core.database.RecordingSessionEntity]. That status
 * is written and observed through Room, and a fast on-device transcription can write
 * TRANSCRIBING and overwrite it with TRANSCRIBED before Room's `InvalidationTracker` gets a
 * chance to run the observing query in between — the UI would then miss the whole thing (cloud
 * transcription's own network latency happened to always mask this race, which is why it went
 * unnoticed until local got a progress footer). [dev.scrybe.core.transcription.SessionTranscriptionCoordinator]
 * registers/unregisters synchronously around every transcribe call (auto, manual retry, batch,
 * or the session-detail button all funnel through it), so
 * [TranscriptionCancellationController.activeSessionIds] reflects "running right now" with no
 * query/coalescing step that could lose a fast transition.
 *
 * The DB status Flow is kept only to source [TranscriptionProgressUiState.label] (the current
 * session's title) — losing that for an especially fast local transcription just means the
 * footer briefly shows no title, not that it fails to show at all.
 *
 * [TranscriptionCancellationController.activeSessionIds] alone undercounts a "transcribe
 * selected" batch, though: such a batch processes one session at a time, so only one id is ever
 * registered — the rest of the batch is otherwise indistinguishable from "nothing else
 * pending." [BatchTranscriptionTracker] fills that specific gap; combined, [queuedCount] covers
 * both a same-batch backlog and any other transcription that happens to be running concurrently
 * (e.g. auto-transcribe firing while a manual retry is in flight).
 */
@HiltViewModel
class TranscriptionProgressViewModel
    @Inject
    constructor(
        recordingSessionDao: RecordingSessionDao,
        batchTranscriptionTracker: BatchTranscriptionTracker,
        chunkProgressTracker: TranscriptionChunkProgressTracker,
        private val cancellationController: TranscriptionCancellationController,
    ) : ViewModel() {
        // Purely a local "did the tap register" signal — there's no DB/coordinator state for
        // "a cancel was requested but the in-flight native decode hasn't noticed yet" to derive
        // this from (see WhisperEngine's chunk-boundary-only cancellation checkpoints), so it's
        // owned here rather than added as a new SessionStatus.
        private val cancellingFlow = MutableStateFlow(false)
        private var cancellingTimeoutJob: Job? = null

        /** Deliberately excludes [cancellingFlow] so nothing here loops back into it. */
        private val transcribingState: StateFlow<TranscriptionProgressUiState> =
            combine(
                cancellationController.activeSessionIds,
                recordingSessionDao.observeSessionsByStatus(SessionStatus.TRANSCRIBING.name),
                batchTranscriptionTracker.remaining,
                chunkProgressTracker.progress,
            ) { activeSessionIds, sessions, batchRemaining, chunkProgress ->
                val current = sessions.firstOrNull()
                TranscriptionProgressUiState(
                    isTranscribing = activeSessionIds.isNotEmpty() || batchRemaining > 0,
                    label = current?.title.orEmpty(),
                    queuedCount = (activeSessionIds.size - 1).coerceAtLeast(0) + batchRemaining,
                    chunkProgress = chunkProgress,
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
