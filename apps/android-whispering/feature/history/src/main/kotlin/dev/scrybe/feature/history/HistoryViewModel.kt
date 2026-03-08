package dev.scrybe.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    recordingSessionDao: RecordingSessionDao,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = recordingSessionDao.getAllSessions()
        .map { entities ->
            val sessions = entities.map { entity ->
                RecordingSession(
                    id = entity.id,
                    title = entity.title,
                    audioFilePath = entity.audioFilePath,
                    durationMs = entity.durationMs,
                    fileSizeBytes = entity.fileSizeBytes,
                    audioFormat = AudioFormat.valueOf(entity.audioFormat),
                    status = SessionStatus.valueOf(entity.status),
                    createdAt = Instant.ofEpochMilli(entity.createdAt),
                    updatedAt = Instant.ofEpochMilli(entity.updatedAt),
                )
            }
            HistoryUiState.Success(sessions) as HistoryUiState
        }
        .catch { emit(HistoryUiState.Error(it.message ?: "Unknown error")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState.Loading,
        )
}
