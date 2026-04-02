package dev.scrybe.feature.sessiondetail

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.audio.AudioPlayer
import dev.scrybe.core.common.TagsCodec
import dev.scrybe.core.common.TransformStepsCodec
import dev.scrybe.core.common.WaveformCodec
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.database.TranscriptEntity
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TransformRunDao
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.export.ExportCoordinator
import dev.scrybe.core.export.ExportFormat
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.Transcript
import dev.scrybe.core.model.TranscriptType
import dev.scrybe.core.model.TransformProfile
import dev.scrybe.core.transcription.SessionTranscriptionCoordinator
import dev.scrybe.core.transforms.OpenAiTagSuggestionService
import dev.scrybe.core.transforms.SessionTransformCoordinator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SessionDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val sessionDao: RecordingSessionDao,
        private val transcriptDao: TranscriptDao,
        private val transformProfileDao: TransformProfileDao,
        private val transformRunDao: TransformRunDao,
        private val preferencesDataStore: AppPreferencesDataStore,
        private val sessionTranscriptionCoordinator: SessionTranscriptionCoordinator,
        private val sessionTransformCoordinator: SessionTransformCoordinator,
        private val tagSuggestionService: OpenAiTagSuggestionService,
        private val exportCoordinator: ExportCoordinator,
        private val audioPlayer: AudioPlayer,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

        private val _uiState = MutableStateFlow<SessionDetailUiState>(SessionDetailUiState.Loading)
        val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()
        private val isTransforming = MutableStateFlow(false)
        private val renamePromptDismissed = MutableStateFlow(false)
        private val tagSuggestionState = MutableStateFlow<TagSuggestionUiState>(TagSuggestionUiState.Idle)
        private val _events = MutableSharedFlow<SessionDetailEvent>()
        val events = _events.asSharedFlow()

        init {
            viewModelScope.launch {
                val session = sessionDao.getSessionByIdOnce(sessionId)
                if (session?.status == SessionStatus.TRANSCRIBING.name) {
                    sessionDao.updateSession(
                        session.copy(
                            status = SessionStatus.FAILED.name,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                val playbackBundle =
                    combine(
                        audioPlayer.playbackState,
                        preferencesDataStore.showRenameAfterRecording,
                        renamePromptDismissed,
                    ) { playbackState, showRenameAfterRecording, renamePromptDismissed ->
                        Triple(playbackState, showRenameAfterRecording, renamePromptDismissed)
                    }
                combine(
                    combine(
                        sessionDao.getSessionById(sessionId),
                        transcriptDao.getTranscriptsForSession(sessionId),
                        transformProfileDao.getAllProfiles(),
                    ) { sessionEntity, transcriptEntities, profileEntities ->
                        Triple(sessionEntity, transcriptEntities, profileEntities)
                    },
                    preferencesDataStore.defaultTransformProfileId,
                    isTransforming,
                    playbackBundle,
                    tagSuggestionState,
                ) { sessionBundle, defaultProfileId, isTransforming, playbackBundle, tagSuggestionState ->
                    val (sessionEntity, transcriptEntities, profileEntities) = sessionBundle
                    val (playbackState, showRenameAfterRecording, renamePromptDismissed) = playbackBundle
                    if (sessionEntity == null) {
                        SessionDetailUiState.Error("Session not found")
                    } else {
                        val session =
                            RecordingSession(
                                id = sessionEntity.id,
                                title = sessionEntity.title,
                                tags = TagsCodec.decode(sessionEntity.tags),
                                audioFilePath = sessionEntity.audioFilePath,
                                durationMs = sessionEntity.durationMs,
                                fileSizeBytes = sessionEntity.fileSizeBytes,
                                audioFormat = AudioFormat.valueOf(sessionEntity.audioFormat),
                                sampleRateHz = sessionEntity.sampleRateHz,
                                encodingBitRate = sessionEntity.encodingBitRate,
                                channelCount = sessionEntity.channelCount,
                                waveformSamples = WaveformCodec.decode(sessionEntity.waveformSamples),
                                status = SessionStatus.valueOf(sessionEntity.status),
                                isArchived = sessionEntity.isArchived,
                                estimatedTranscriptionCostUsd = sessionEntity.estimatedTranscriptionCostUsd,
                                createdAt = Instant.ofEpochMilli(sessionEntity.createdAt),
                                updatedAt = Instant.ofEpochMilli(sessionEntity.updatedAt),
                            )
                        val transcripts =
                            transcriptEntities.map { entity ->
                                Transcript(
                                    id = entity.id,
                                    sessionId = entity.sessionId,
                                    content = entity.content,
                                    type = TranscriptType.valueOf(entity.type),
                                    sourceTranscriptId = entity.sourceTranscriptId,
                                    providerType = entity.providerType?.let { ProviderType.valueOf(it) },
                                    transformProfileId = entity.transformProfileId,
                                    transformRunId = entity.transformRunId,
                                    createdAt = Instant.ofEpochMilli(entity.createdAt),
                                )
                            }
                        val profiles =
                            profileEntities.map { entity ->
                                TransformProfile(
                                    id = entity.id,
                                    name = entity.name,
                                    description = entity.description,
                                    systemPrompt = entity.systemPrompt,
                                    steps = TransformStepsCodec.decode(entity.steps, fallback = entity.systemPrompt),
                                    providerType = ProviderType.valueOf(entity.providerType),
                                    isDefault = entity.isDefault,
                                )
                            }
                        val originalTranscript =
                            transcripts
                                .filter { it.type == TranscriptType.RAW }
                                .maxByOrNull { it.createdAt }
                        val currentTranscript =
                            transcripts
                                .filter { it.type == TranscriptType.EDITED }
                                .maxByOrNull { it.createdAt }
                                ?: originalTranscript
                        SessionDetailUiState.Success(
                            session = session,
                            transcripts = transcripts,
                            originalTranscript = originalTranscript,
                            currentTranscript = currentTranscript,
                            profiles = profiles,
                            defaultProfileId = defaultProfileId,
                            isTranscribing = session.status == SessionStatus.TRANSCRIBING,
                            isTransforming = isTransforming,
                            isPlaying = playbackState.filePath == session.audioFilePath && playbackState.isPlaying,
                            playbackPositionMs =
                                if (playbackState.filePath == session.audioFilePath) {
                                    playbackState.currentPositionMs
                                } else {
                                    0L
                                },
                            playbackDurationMs =
                                if (playbackState.filePath == session.audioFilePath) {
                                    playbackState.durationMs
                                } else {
                                    session.durationMs
                                },
                            shouldPromptForRename =
                                shouldPromptForRename(
                                    session = session,
                                    showRenameAfterRecording = showRenameAfterRecording,
                                    renamePromptDismissed = renamePromptDismissed,
                                ),
                            tagSuggestionState = tagSuggestionState,
                        )
                    }
                }
                    .catch { emit(SessionDetailUiState.Error(it.message ?: "Unknown error")) }
                    .collect { _uiState.value = it }
            }
        }

        fun transform(profileId: String) {
            viewModelScope.launch {
                isTransforming.value = true
                sessionTransformCoordinator.transformLatestRawTranscript(sessionId, profileId)
                    .onSuccess {
                        _events.emit(SessionDetailEvent.Message("Transform completed."))
                    }
                    .onFailure {
                        Log.e(TAG, "Transform failed for session $sessionId", it)
                        _events.emit(SessionDetailEvent.Message(it.message ?: "Transform failed"))
                    }
                isTransforming.value = false
            }
        }

        fun transformDefaultProfile() {
            val state = _uiState.value as? SessionDetailUiState.Success ?: return
            val defaultProfileId =
                state.defaultProfileId
                    ?: state.profiles.firstOrNull()?.id
            if (defaultProfileId == null) {
                viewModelScope.launch {
                    _events.emit(SessionDetailEvent.Message("Create a profile before transforming"))
                }
                return
            }
            transform(defaultProfileId)
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

        fun resetTranscriptionState() {
            viewModelScope.launch {
                val session = sessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                sessionDao.updateSession(
                    session.copy(
                        status = SessionStatus.RECORDED.name,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                _events.emit(SessionDetailEvent.Message("Transcription state cleared"))
            }
        }

        fun setArchived(archived: Boolean) {
            viewModelScope.launch {
                val session = sessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                sessionDao.updateSession(
                    session.copy(
                        isArchived = archived,
                        status = if (archived) SessionStatus.ARCHIVED.name else restoreStatus(session.status),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                _events.emit(SessionDetailEvent.Message(if (archived) "Recording archived" else "Recording restored"))
            }
        }

        private fun restoreStatus(status: String): String {
            val current = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.RECORDED)
            return if (current == SessionStatus.ARCHIVED) SessionStatus.RECORDED.name else current.name
        }

        fun exportAll() {
            val state = _uiState.value as? SessionDetailUiState.Success ?: return
            viewModelScope.launch {
                val outputDir =
                    File(
                        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
                        "exports",
                    )

                val exports =
                    listOf(
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
                val transcript = state.currentTranscript
                if (transcript == null) {
                    _events.emit(SessionDetailEvent.Message("No transcript available to share"))
                } else {
                    _events.emit(
                        SessionDetailEvent.ShareText(
                            title = state.session.title,
                            text = transcript.content,
                        ),
                    )
                }
            }
        }

        fun shareAudioFile() {
            val state = _uiState.value as? SessionDetailUiState.Success ?: return
            viewModelScope.launch {
                val audioPath = state.session.audioFilePath
                val audioFile = File(audioPath)
                if (!audioFile.exists()) {
                    _events.emit(SessionDetailEvent.Message("Audio file is no longer available"))
                } else {
                    _events.emit(
                        SessionDetailEvent.ShareFile(
                            title = state.session.title,
                            path = audioPath,
                            mimeType = audioMimeTypeFor(audioFile.extension),
                        ),
                    )
                }
            }
        }

        fun togglePlayback() {
            val state = _uiState.value as? SessionDetailUiState.Success ?: return
            viewModelScope.launch {
                if (state.isPlaying) {
                    audioPlayer.pause()
                } else {
                    audioPlayer.play(state.session.audioFilePath)
                        .onFailure {
                            _events.emit(SessionDetailEvent.Message(it.message ?: "Playback failed"))
                        }
                }
            }
        }

        fun seekPlayback(positionMs: Long) {
            audioPlayer.seekTo(positionMs)
        }

        fun stopPlayback() {
            audioPlayer.stop()
        }

        fun renameSession(newTitle: String) {
            val trimmed = newTitle.trim()
            if (trimmed.isBlank()) return
            viewModelScope.launch {
                val session = sessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                sessionDao.updateSession(
                    session.copy(
                        title = trimmed,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                renamePromptDismissed.value = true
                _events.emit(SessionDetailEvent.Message("Recording renamed"))
            }
        }

        fun saveTags(tagsInput: String) {
            val normalizedTags = TagsCodec.normalizeInput(tagsInput)
            viewModelScope.launch {
                val session = sessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                sessionDao.updateSession(
                    session.copy(
                        tags = TagsCodec.encode(normalizedTags),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                tagSuggestionState.value = TagSuggestionUiState.Idle
                _events.emit(
                    SessionDetailEvent.Message(
                        if (normalizedTags.isEmpty()) "Tags cleared" else "Saved ${normalizedTags.size} tags",
                    ),
                )
            }
        }

        fun suggestTags() {
            val state = _uiState.value as? SessionDetailUiState.Success ?: return
            val transcriptText = state.currentTranscript?.content ?: state.originalTranscript?.content
            if (transcriptText.isNullOrBlank()) {
                viewModelScope.launch {
                    _events.emit(SessionDetailEvent.Message("Transcribe this recording before suggesting tags"))
                }
                return
            }

            viewModelScope.launch {
                tagSuggestionState.value = TagSuggestionUiState.Loading
                tagSuggestionService.suggestTags(
                    title = state.session.title,
                    transcriptText = transcriptText,
                    existingTags = state.session.tags,
                ).fold(
                    onSuccess = { suggestedTags ->
                        tagSuggestionState.value = TagSuggestionUiState.Success(suggestedTags)
                    },
                    onFailure = { error ->
                        tagSuggestionState.value =
                            TagSuggestionUiState.Error(error.message ?: "Failed to suggest tags")
                    },
                )
            }
        }

        fun clearTagSuggestionState() {
            tagSuggestionState.value = TagSuggestionUiState.Idle
        }

        fun saveTranscriptEdit(content: String) {
            val trimmed = content.trim()
            if (trimmed.isBlank()) return
            viewModelScope.launch {
                val existingEdited = transcriptDao.getLatestTranscriptByType(sessionId, TranscriptType.EDITED.name)
                val rawTranscript = transcriptDao.getLatestTranscriptByType(sessionId, TranscriptType.RAW.name)
                if (rawTranscript == null) {
                    _events.emit(SessionDetailEvent.Message("Transcribe this record before editing"))
                    return@launch
                }

                transcriptDao.insertTranscript(
                    TranscriptEntity(
                        id = existingEdited?.id ?: java.util.UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        content = trimmed,
                        type = TranscriptType.EDITED.name,
                        sourceTranscriptId = rawTranscript.id,
                        providerType = rawTranscript.providerType,
                        transformProfileId = null,
                        transformRunId = null,
                        createdAt = System.currentTimeMillis(),
                    ),
                )

                val session = sessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                sessionDao.updateSession(
                    session.copy(
                        status = SessionStatus.EDITED.name,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                _events.emit(SessionDetailEvent.Message("Transcript saved"))
            }
        }

        fun deleteTranscript(transcriptId: String) {
            viewModelScope.launch {
                val transcript = transcriptDao.getTranscriptById(transcriptId) ?: return@launch
                val allTranscripts = transcriptDao.getTranscriptsForSession(sessionId).first()
                when (TranscriptType.valueOf(transcript.type)) {
                    TranscriptType.RAW -> {
                        allTranscripts.forEach { item ->
                            item.transformRunId?.let { transformRunDao.deleteRun(it) }
                        }
                        transformRunDao.deleteRunsForSession(sessionId)
                        transcriptDao.deleteTranscriptsForSession(sessionId)
                    }
                    TranscriptType.EDITED -> {
                        val dependentTransforms =
                            allTranscripts.filter { item ->
                                item.type == TranscriptType.TRANSFORMED.name && item.sourceTranscriptId == transcript.id
                            }
                        dependentTransforms.forEach { item ->
                            transcriptDao.deleteTranscript(item.id)
                            item.transformRunId?.let { transformRunDao.deleteRun(it) }
                        }
                        transcriptDao.deleteTranscript(transcriptId)
                    }
                    TranscriptType.TRANSFORMED -> {
                        transcriptDao.deleteTranscript(transcriptId)
                        transcript.transformRunId?.let { transformRunDao.deleteRun(it) }
                    }
                }

                val remaining = transcriptDao.getTranscriptsForSession(sessionId).first()
                val session = sessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                val nextStatus =
                    when {
                        remaining.any { it.type == TranscriptType.EDITED.name } -> SessionStatus.EDITED
                        remaining.any { it.type == TranscriptType.RAW.name } -> SessionStatus.TRANSCRIBED
                        session.status == SessionStatus.FAILED.name -> SessionStatus.FAILED
                        else -> SessionStatus.RECORDED
                    }
                sessionDao.updateSession(
                    session.copy(
                        status = nextStatus.name,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )

                val message =
                    when (TranscriptType.valueOf(transcript.type)) {
                        TranscriptType.TRANSFORMED -> "Transformation deleted"
                        TranscriptType.EDITED -> "Edited transcript deleted"
                        TranscriptType.RAW -> "Transcript deleted"
                    }
                _events.emit(SessionDetailEvent.Message(message))
            }
        }

        fun dismissRenamePrompt() {
            renamePromptDismissed.value = true
        }

        override fun onCleared() {
            audioPlayer.stop()
            super.onCleared()
        }

        private companion object {
            const val TAG = "SessionDetailViewModel"
            val DEFAULT_TITLE_PREFIX = "Recording "
            val RENAME_PROMPT_WINDOW: Duration = Duration.ofMinutes(10)

            fun audioMimeTypeFor(extension: String): String =
                when (extension.lowercase()) {
                    "m4a", "mp4" -> "audio/mp4"
                    "ogg" -> "audio/ogg"
                    "webm" -> "audio/webm"
                    else -> "audio/*"
                }

            fun shouldPromptForRename(
                session: RecordingSession,
                showRenameAfterRecording: Boolean,
                renamePromptDismissed: Boolean,
            ): Boolean {
                if (!showRenameAfterRecording || renamePromptDismissed) return false
                if (!session.title.startsWith(DEFAULT_TITLE_PREFIX)) return false
                return Duration.between(session.createdAt, Instant.now()) <= RENAME_PROMPT_WINDOW
            }
        }
    }
