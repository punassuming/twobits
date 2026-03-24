package dev.scrybe.feature.capture

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.audio.AudioRecorder
import dev.scrybe.service.recording.RecordingForegroundService
import dev.scrybe.service.recording.RecordingServiceActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRecorder: AudioRecorder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            audioRecorder.telemetry.collectLatest { telemetry ->
                val currentState = _uiState.value
                val nextPhase = when {
                    telemetry.elapsedMs > 0L || telemetry.amplitudeRatio > 0f -> CapturePhase.RECORDING
                    currentState.phase == CapturePhase.STOPPING -> CapturePhase.IDLE
                    else -> currentState.phase
                }
                val nextHistory = when (nextPhase) {
                    CapturePhase.RECORDING -> (currentState.amplitudeHistory + telemetry.amplitudeRatio)
                        .takeLast(MAX_HISTORY)
                    else -> emptyList()
                }
                _uiState.value = currentState.copy(
                    phase = nextPhase,
                    elapsedMs = telemetry.elapsedMs,
                    amplitudeHistory = nextHistory,
                )
            }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            _uiState.value = CaptureUiState(phase = CapturePhase.RECORDING)
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = RecordingServiceActions.ACTION_START
            }
            context.startForegroundService(intent)
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(phase = CapturePhase.STOPPING)
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = RecordingServiceActions.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    fun cancelRecording() {
        viewModelScope.launch {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = RecordingServiceActions.ACTION_CANCEL
            }
            context.startService(intent)
            _uiState.value = CaptureUiState()
        }
    }

    private companion object {
        const val MAX_HISTORY = 32
    }
}
