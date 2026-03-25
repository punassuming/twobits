package dev.scrybe.feature.capture

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.audio.AudioRecorder
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.service.recording.RecordingForegroundService
import dev.scrybe.service.recording.RecordingServiceActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRecorder: AudioRecorder,
    private val recordingSessionDao: RecordingSessionDao,
    private val transcriptDao: TranscriptDao,
    private val preferencesDataStore: AppPreferencesDataStore,
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
                    currentAmplitudeRatio = telemetry.amplitudeRatio,
                    amplitudeHistory = nextHistory,
                )
            }
        }
        viewModelScope.launch {
            audioRecorder.isRecording.collectLatest { isRecording ->
                if (!isRecording && _uiState.value.phase == CapturePhase.RECORDING) {
                    _uiState.value = _uiState.value.copy(
                        phase = CapturePhase.IDLE,
                        elapsedMs = 0L,
                        currentAmplitudeRatio = 0f,
                        amplitudeHistory = emptyList(),
                    )
                }
            }
        }
        viewModelScope.launch {
            preferencesDataStore.keepScreenOn.collectLatest { enabled ->
                _uiState.value = _uiState.value.copy(keepScreenOn = enabled)
            }
        }
        viewModelScope.launch {
            combine(
                recordingSessionDao.getActiveSessions(),
                transcriptDao.getAllTranscripts(),
            ) { sessions, transcripts ->
                val transcriptLookup = transcripts
                    .groupBy { it.sessionId }
                    .mapValues { (_, values) ->
                        values
                            .sortedByDescending { it.createdAt }
                            .firstOrNull { it.type == "EDITED" }
                            ?.content
                            ?: values.maxByOrNull { it.createdAt }?.content
                    }
                sessions.take(3).map { session ->
                    RecentCaptureSession(
                        id = session.id,
                        title = session.title,
                        createdAtLabel = java.time.Instant.ofEpochMilli(session.createdAt)
                            .atZone(ZoneId.systemDefault())
                            .format(RECENT_TIME_FORMATTER),
                        status = dev.scrybe.core.model.SessionStatus.valueOf(session.status),
                        transcriptPreview = transcriptLookup[session.id],
                    )
                }
            }.collectLatest { recentSessions ->
                _uiState.value = _uiState.value.copy(recentSessions = recentSessions)
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
        val RECENT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
    }
}
