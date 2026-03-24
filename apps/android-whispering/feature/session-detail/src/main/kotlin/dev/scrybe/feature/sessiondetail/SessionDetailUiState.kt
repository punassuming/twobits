package dev.scrybe.feature.sessiondetail

import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.TransformProfile
import dev.scrybe.core.model.Transcript

sealed interface SessionDetailUiState {
    data object Loading : SessionDetailUiState
    data class Success(
        val session: RecordingSession,
        val transcripts: List<Transcript>,
        val profiles: List<TransformProfile>,
        val defaultProfileId: String?,
        val isTranscribing: Boolean = false,
        val isTransforming: Boolean = false,
        val isPlaying: Boolean = false,
        val playbackPositionMs: Long = 0L,
        val playbackDurationMs: Long = 0L,
        val shouldPromptForRename: Boolean = false,
    ) : SessionDetailUiState
    data class Error(val message: String) : SessionDetailUiState
}
