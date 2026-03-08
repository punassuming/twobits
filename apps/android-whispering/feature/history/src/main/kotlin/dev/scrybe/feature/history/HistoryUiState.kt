package dev.scrybe.feature.history

import dev.scrybe.core.model.RecordingSession

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(val sessions: List<RecordingSession>) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}
