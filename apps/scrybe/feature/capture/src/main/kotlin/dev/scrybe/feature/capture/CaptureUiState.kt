package dev.scrybe.feature.capture

import dev.scrybe.core.model.CustomRecordingType
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
    val activeCustomTypeName: String? = null,
    val folderNames: Map<String, String> = emptyMap(),
    val selectedSessionIds: Set<String> = emptySet(),
    val customTypes: List<CustomRecordingType> = emptyList(),
    val minimized: Boolean = false,
    val autoTranscribeEnabled: Boolean = true,
    val liveTranscript: String? = null,
    val activeSessionId: String? = null,
    val streamingPartialTranscript: String? = null,
    val streamingStatus: LiveStreamStatus = LiveStreamStatus.OFF,
) {
    val isSelecting: Boolean get() = selectedSessionIds.isNotEmpty()
}

/**
 * Live realtime-transcription connection status for the recording currently in progress.
 * Deliberately separate from [CaptureUiState.liveTranscript], whose existing post-stop-only
 * semantics (shown during [CapturePhase.STOPPING]) must not change.
 */
enum class LiveStreamStatus {
    /** Local/OFF tier, or streaming hasn't been attempted yet — no live preview available. */
    OFF,
    CONNECTING,
    STREAMING,
    UNAVAILABLE,
    DROPPED,
}

data class CaptureTransformDialogState(
    val sessionIds: List<String>,
    val sessionTitles: List<String>,
    val runningProfileId: String? = null,
    val result: CaptureTransformResult? = null,
)

data class CaptureTransformResult(
    val profileName: String,
    val text: String,
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
    val folderId: String? = null,
    val speakerCount: Int = 0,
    val openTaskCount: Int = 0,
    val waveformSamples: List<Float> = emptyList(),
)

enum class CapturePhase {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPING,
}
