package dev.scrybe.feature.capture

import dev.scrybe.core.model.SessionStatus

data class CaptureUiState(
    val phase: CapturePhase = CapturePhase.IDLE,
    val elapsedMs: Long = 0L,
    val currentAmplitudeRatio: Float = 0f,
    val amplitudeHistory: List<Float> = emptyList(),
    val keepScreenOn: Boolean = true,
    val recentSessions: List<RecentCaptureSession> = emptyList(),
    val errorMessage: String? = null,
)

data class RecentCaptureSession(
    val id: String,
    val title: String,
    val createdAtLabel: String,
    val status: SessionStatus,
    val transcriptPreview: String?,
)

enum class CapturePhase {
    IDLE,
    RECORDING,
    STOPPING,
}
