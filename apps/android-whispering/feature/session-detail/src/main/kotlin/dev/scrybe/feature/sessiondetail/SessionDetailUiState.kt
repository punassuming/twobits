package dev.scrybe.feature.sessiondetail

import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.Transcript

sealed interface SessionDetailUiState {
    data object Loading : SessionDetailUiState
    data class Success(
        val session: RecordingSession,
        val transcripts: List<Transcript>,
        val isTranscribing: Boolean = false,
    ) : SessionDetailUiState
    data class Error(val message: String) : SessionDetailUiState
}
