package dev.scrybe.feature.sessiondetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.Transcript
import dev.scrybe.core.model.TranscriptType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionDao: RecordingSessionDao,
    private val transcriptDao: TranscriptDao,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _uiState = MutableStateFlow<SessionDetailUiState>(SessionDetailUiState.Loading)
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionDao.getSessionById(sessionId),
                transcriptDao.getTranscriptsForSession(sessionId),
            ) { sessionEntity, transcriptEntities ->
                if (sessionEntity == null) {
                    SessionDetailUiState.Error("Session not found")
                } else {
                    val session = RecordingSession(
                        id = sessionEntity.id,
                        title = sessionEntity.title,
                        audioFilePath = sessionEntity.audioFilePath,
                        durationMs = sessionEntity.durationMs,
                        fileSizeBytes = sessionEntity.fileSizeBytes,
                        audioFormat = AudioFormat.valueOf(sessionEntity.audioFormat),
                        status = SessionStatus.valueOf(sessionEntity.status),
                        createdAt = Instant.ofEpochMilli(sessionEntity.createdAt),
                        updatedAt = Instant.ofEpochMilli(sessionEntity.updatedAt),
                    )
                    val transcripts = transcriptEntities.map { entity ->
                        Transcript(
                            id = entity.id,
                            sessionId = entity.sessionId,
                            content = entity.content,
                            type = TranscriptType.valueOf(entity.type),
                            providerType = entity.providerType?.let { ProviderType.valueOf(it) },
                            transformProfileId = entity.transformProfileId,
                            transformRunId = entity.transformRunId,
                            createdAt = Instant.ofEpochMilli(entity.createdAt),
                        )
                    }
                    SessionDetailUiState.Success(session = session, transcripts = transcripts)
                }
            }
            .catch { emit(SessionDetailUiState.Error(it.message ?: "Unknown error")) }
            .collect { _uiState.value = it }
        }
    }
}
