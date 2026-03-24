package dev.scrybe.feature.capture

data class CaptureUiState(
    val phase: CapturePhase = CapturePhase.IDLE,
    val elapsedMs: Long = 0L,
    val amplitudeHistory: List<Float> = emptyList(),
    val keepScreenOn: Boolean = true,
    val errorMessage: String? = null,
)

sealed interface CaptureEvent {
    data class OpenSessionDetail(val sessionId: String) : CaptureEvent
}

enum class CapturePhase {
    IDLE,
    RECORDING,
    STOPPING,
}
