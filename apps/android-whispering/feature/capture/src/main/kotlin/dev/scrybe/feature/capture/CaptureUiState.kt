package dev.scrybe.feature.capture

import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.SessionStatus

data class CaptureUiState(
    val phase: CapturePhase = CapturePhase.IDLE,
    val elapsedMs: Long = 0L,
    val currentAmplitudeRatio: Float = 0f,
    val amplitudeHistory: List<Float> = emptyList(),
    val keepScreenOn: Boolean = true,
    val recentSessions: List<RecentCaptureSession> = emptyList(),
    val errorMessage: String? = null,
    val showModePickerSheet: Boolean = false,
    val openTaskTotal: Int = 0,
    val activeMode: RecordingMode = RecordingMode.JOURNAL,
)

data class RecentCaptureSession(
    val id: String,
    val title: String,
    val createdAtLabel: String,
    val durationMs: Long,
    val status: SessionStatus,
    val mode: RecordingMode,
    val tags: List<String>,
    val locationLabel: String?,
    val transcriptPreview: String?,
    val isArchived: Boolean,
    val speakerCount: Int = 0,
    val openTaskCount: Int = 0,
)

enum class CapturePhase {
    IDLE,
    RECORDING,
    STOPPING,
}
