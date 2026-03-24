package dev.scrybe.feature.capture

data class CaptureUiState(
    val phase: CapturePhase = CapturePhase.IDLE,
    val elapsedMs: Long = 0L,
    val amplitudeHistory: List<Float> = emptyList(),
    val errorMessage: String? = null,
)

enum class CapturePhase {
    IDLE,
    RECORDING,
    STOPPING,
}
