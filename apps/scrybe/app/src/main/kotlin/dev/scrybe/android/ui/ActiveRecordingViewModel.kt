package dev.scrybe.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.audio.AudioRecorder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ActiveRecordingUiState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val amplitudeRatio: Float = 0f,
)

@HiltViewModel
class ActiveRecordingViewModel
    @Inject
    constructor(
        audioRecorder: AudioRecorder,
    ) : ViewModel() {
        val uiState: StateFlow<ActiveRecordingUiState> =
            combine(
                audioRecorder.isRecording,
                audioRecorder.telemetry,
            ) { isRecording, telemetry ->
                ActiveRecordingUiState(
                    isRecording = isRecording,
                    elapsedMs = telemetry.elapsedMs,
                    amplitudeRatio = telemetry.amplitudeRatio,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ActiveRecordingUiState(),
            )
    }
