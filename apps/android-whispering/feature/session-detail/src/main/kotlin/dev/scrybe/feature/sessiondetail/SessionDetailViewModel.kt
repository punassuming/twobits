package dev.scrybe.feature.sessiondetail

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.audio.AudioPlayer
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.export.ExportCoordinator
import dev.scrybe.core.export.ExportFormat
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.Transcript
import dev.scrybe.core.model.TranscriptType
import dev.scrybe.core.transcription.SessionTranscriptionCoordinator
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val sessionTranscriptionCoordinator: SessionTranscriptionCoordinator,
    private val exportCoordinator: ExportCoordinator,
    private val audioPlayer: AudioPlayer,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _uiState = MutableStateFlow<SessionDetailUiState>(SessionDetailUiState.Loading)
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<SessionDetailEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionDao.getSessionById(sessionId),
                transcriptDao.getTranscriptsForSession(sessionId),
                audioPlayer.isPlaying,
            ) { sessionEntity, transcriptEntities, isPlaying ->
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
                    SessionDetailUiState.Success(
                        session = session,
                        transcripts = transcripts,
                        isTranscribing = session.status == SessionStatus.TRANSCRIBING,
                        isPlaying = isPlaying,
                    )
                }
            }
            .catch { emit(SessionDetailUiState.Error(it.message ?: "Unknown error")) }
            .collect { _uiState.value = it }
        }
    }

    fun transcribe() {
        viewModelScope.launch {
            sessionTranscriptionCoordinator.transcribeSession(sessionId)
                .onSuccess {
                    _events.emit(SessionDetailEvent.Message("Transcript created."))
                }
                .onFailure {
                    Log.e(TAG, "Transcription failed for session $sessionId", it)
                    _events.emit(SessionDetailEvent.Message(it.message ?: "Transcription failed"))
                }
        }
    }

    fun exportAll() {
        val state = _uiState.value as? SessionDetailUiState.Success ?: return
        viewModelScope.launch {
            val outputDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
                "exports",
            )

            val exports = listOf(
                exportCoordinator.export(state.session, state.transcripts, ExportFormat.MARKDOWN, outputDir),
                exportCoordinator.export(state.session, state.transcripts, ExportFormat.TXT, outputDir),
                exportCoordinator.export(state.session, state.transcripts, ExportFormat.JSON, outputDir),
            )

            val failure = exports.firstOrNull { it.isFailure }?.exceptionOrNull()
            if (failure != null) {
                _events.emit(SessionDetailEvent.Message(failure.message ?: "Export failed"))
            } else {
                _events.emit(SessionDetailEvent.Message("Exported files to ${outputDir.absolutePath}"))
            }
        }
    }

    fun shareLatestTranscript() {
        val state = _uiState.value as? SessionDetailUiState.Success ?: return
        viewModelScope.launch {
            val transcript = state.transcripts.maxByOrNull { it.createdAt }
            if (transcript == null) {
                _events.emit(SessionDetailEvent.Message("No transcript available to share"))
            } else {
                _events.emit(
                    SessionDetailEvent.ShareText(
                        title = state.session.title,
                        text = transcript.content,
                    )
                )
            }
        }
    }

    fun togglePlayback() {
        val state = _uiState.value as? SessionDetailUiState.Success ?: return
        viewModelScope.launch {
            if (state.isPlaying) {
                audioPlayer.stop()
            } else {
                audioPlayer.play(state.session.audioFilePath)
                    .onFailure {
                        _events.emit(SessionDetailEvent.Message(it.message ?: "Playback failed"))
                    }
            }
        }
    }

    override fun onCleared() {
        audioPlayer.stop()
        super.onCleared()
    }

    private companion object {
        const val TAG = "SessionDetailViewModel"
    }
}
