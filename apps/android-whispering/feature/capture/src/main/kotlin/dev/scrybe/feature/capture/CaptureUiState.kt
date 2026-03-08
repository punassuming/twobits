package dev.scrybe.feature.capture

sealed interface CaptureUiState {
    data object Idle : CaptureUiState
    data object Recording : CaptureUiState
    data object Stopping : CaptureUiState
    data class Failed(val message: String) : CaptureUiState
}
