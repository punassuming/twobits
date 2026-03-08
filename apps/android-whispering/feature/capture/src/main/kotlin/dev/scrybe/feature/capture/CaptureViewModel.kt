package dev.scrybe.feature.capture

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.service.recording.RecordingForegroundService
import dev.scrybe.service.recording.RecordingServiceActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Idle)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun startRecording() {
        viewModelScope.launch {
            _uiState.value = CaptureUiState.Recording
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = RecordingServiceActions.ACTION_START
            }
            context.startForegroundService(intent)
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            _uiState.value = CaptureUiState.Stopping
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = RecordingServiceActions.ACTION_STOP
            }
            context.startService(intent)
            _uiState.value = CaptureUiState.Idle
        }
    }

    fun cancelRecording() {
        viewModelScope.launch {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = RecordingServiceActions.ACTION_CANCEL
            }
            context.startService(intent)
            _uiState.value = CaptureUiState.Idle
        }
    }
}
