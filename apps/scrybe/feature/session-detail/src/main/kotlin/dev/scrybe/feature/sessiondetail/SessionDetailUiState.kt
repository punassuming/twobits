package dev.scrybe.feature.sessiondetail

import dev.scrybe.core.model.Person
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SentimentSegment
import dev.scrybe.core.model.SessionTask
import dev.scrybe.core.model.SpeakerSegment
import dev.scrybe.core.model.TopicMarker
import dev.scrybe.core.model.Transcript
import dev.scrybe.core.model.TransformProfile
import dev.scrybe.core.transcription.DiarizationDebugInfo

data class AnalysisSuggestionState(
    val suggestedTitle: String? = null,
    val suggestedTags: List<String> = emptyList(),
    val suggestedMode: RecordingMode? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface TagSuggestionUiState {
    data object Idle : TagSuggestionUiState

    data object Loading : TagSuggestionUiState

    data class Success(
        val tags: List<String>,
    ) : TagSuggestionUiState

    data class Error(
        val message: String,
    ) : TagSuggestionUiState
}

sealed interface SessionDetailUiState {
    data object Loading : SessionDetailUiState

    data class Success(
        val session: RecordingSession,
        val transcripts: List<Transcript>,
        val originalTranscript: Transcript?,
        val currentTranscript: Transcript?,
        val profiles: List<TransformProfile>,
        val defaultProfileId: String?,
        val isTranscribing: Boolean = false,
        val isTransforming: Boolean = false,
        val isPlaying: Boolean = false,
        val playbackPositionMs: Long = 0L,
        val playbackDurationMs: Long = 0L,
        val playbackSpeed: Float = 1.0f,
        val shouldPromptForRename: Boolean = false,
        val tagSuggestionState: TagSuggestionUiState = TagSuggestionUiState.Idle,
        val isFetchingSpeakerInfo: Boolean = false,
        val speakerSegments: List<SpeakerSegment> = emptyList(),
        val persons: List<Person> = emptyList(),
        val sentimentSegments: List<SentimentSegment> = emptyList(),
        val topicMarkers: List<TopicMarker> = emptyList(),
        val densityProfile: List<Float> = emptyList(),
        val tasks: List<SessionTask> = emptyList(),
        val isExtractingTasks: Boolean = false,
        val debugDiarizationEnabled: Boolean = false,
        val diarizationDebug: DiarizationDebugInfo? = null,
    ) : SessionDetailUiState

    data class Error(
        val message: String,
    ) : SessionDetailUiState
}
