package dev.scrybe.feature.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.audio.AudioRecorder
import dev.scrybe.core.common.WaveformCodec
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.database.TransformRunDao
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.TranscriptType
import dev.scrybe.core.transcription.SessionTranscriptionCoordinator
import dev.scrybe.core.transforms.SessionTransformCoordinator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

sealed interface HistoryEvent {
    data class Message(val text: String) : HistoryEvent
}

private data class HistoryUiInputs(
    val query: String,
    val filters: RecordsFilterState,
    val confirmSwipeActions: Boolean,
    val selection: RecordsSelectionState,
)

@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        audioRecorder: AudioRecorder,
        private val recordingSessionDao: RecordingSessionDao,
        private val transcriptDao: TranscriptDao,
        private val transformRunDao: TransformRunDao,
        private val preferencesDataStore: AppPreferencesDataStore,
        private val sessionTransformCoordinator: SessionTransformCoordinator,
        private val sessionTranscriptionCoordinator: SessionTranscriptionCoordinator,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val filters = MutableStateFlow(RecordsFilterState())
        private val selection = MutableStateFlow(RecordsSelectionState())
        private val _events = MutableSharedFlow<HistoryEvent>()
        val events = _events.asSharedFlow()
        val isRecording = audioRecorder.isRecording

        private val historyUiInputs =
            combine(
                query,
                filters,
                preferencesDataStore.confirmRecordSwipeActions,
                selection,
            ) { queryValue, filterState, confirmSwipeActions, selectionState ->
                HistoryUiInputs(
                    query = queryValue,
                    filters = filterState,
                    confirmSwipeActions = confirmSwipeActions,
                    selection = selectionState,
                )
            }

        init {
            viewModelScope.launch {
                recordingSessionDao.updateSessionsByStatus(
                    oldStatus = SessionStatus.TRANSCRIBING.name,
                    newStatus = SessionStatus.FAILED.name,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }

        val uiState: StateFlow<HistoryUiState> =
            combine(
                recordingSessionDao.getAllSessions(),
                transcriptDao.getAllTranscripts(),
                historyUiInputs,
            ) { entities, transcripts, inputs ->
                val sessions =
                    entities.map { entity ->
                        RecordingSession(
                            id = entity.id,
                            title = entity.title,
                            audioFilePath = entity.audioFilePath,
                            durationMs = entity.durationMs,
                            fileSizeBytes = entity.fileSizeBytes,
                            audioFormat = AudioFormat.valueOf(entity.audioFormat),
                            sampleRateHz = entity.sampleRateHz,
                            encodingBitRate = entity.encodingBitRate,
                            channelCount = entity.channelCount,
                            waveformSamples = WaveformCodec.decode(entity.waveformSamples),
                            status = SessionStatus.valueOf(entity.status),
                            isArchived = entity.isArchived,
                            estimatedTranscriptionCostUsd = entity.estimatedTranscriptionCostUsd,
                            createdAt = Instant.ofEpochMilli(entity.createdAt),
                            updatedAt = Instant.ofEpochMilli(entity.updatedAt),
                        )
                    }
                val latestTranscriptBySession =
                    transcripts
                        .groupBy { it.sessionId }
                        .mapValues { (_, items) ->
                            items
                                .sortedByDescending { it.createdAt }
                                .firstOrNull { it.type == TranscriptType.EDITED.name }
                                ?.content
                                ?: items.maxByOrNull { it.createdAt }?.content.orEmpty()
                        }

                val filteredSessions =
                    sessions
                        .filter { session -> session.isArchived == inputs.filters.showArchived }
                        .filter { session -> matchesDateFilter(session, inputs.filters.dateRange) }
                        .filter { session ->
                            inputs.filters.includedStatuses.isEmpty() || session.status in inputs.filters.includedStatuses
                        }
                        .filter { session ->
                            if (inputs.query.isBlank()) {
                                true
                            } else {
                                val searchTerm = inputs.query.trim().lowercase()
                                session.title.lowercase().contains(searchTerm) ||
                                    session.status.name.lowercase().contains(searchTerm) ||
                                    latestTranscriptBySession[session.id].orEmpty().lowercase().contains(searchTerm)
                            }
                        }
                        .sortedWith(comparatorFor(inputs.filters.sortOption))
                        .map { session ->
                            HistorySessionItem(
                                session = session,
                                transcriptPreview = latestTranscriptBySession[session.id],
                            )
                        }

                HistoryUiState.Success(
                    sessions = filteredSessions,
                    filters = inputs.filters,
                    interactionPreferences = RecordsInteractionPreferences(confirmSwipeActions = inputs.confirmSwipeActions),
                    selection =
                        inputs.selection.copy(
                            selectedSessionIds =
                                inputs.selection.selectedSessionIds.intersect(
                                    filteredSessions.map { it.session.id }.toSet(),
                                ),
                        ),
                ) as HistoryUiState
            }
                .catch { emit(HistoryUiState.Error(it.message ?: "Unknown error")) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = HistoryUiState.Loading,
                )

        fun updateQuery(value: String) {
            query.value = value
        }

        fun updateFilters(next: RecordsFilterState) {
            filters.value = next
            selection.value = RecordsSelectionState()
        }

        fun renameSession(
            sessionId: String,
            newTitle: String,
        ) {
            viewModelScope.launch {
                val session = recordingSessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                val trimmedTitle = newTitle.trim()
                if (trimmedTitle.isBlank()) return@launch
                recordingSessionDao.updateSession(
                    session.copy(
                        title = trimmedTitle,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                _events.emit(HistoryEvent.Message("Record renamed"))
            }
        }

        fun deleteSession(sessionId: String) {
            viewModelScope.launch {
                val session = recordingSessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                runCatching {
                    File(session.audioFilePath).takeIf { it.exists() }?.delete()
                    transcriptDao.deleteTranscriptsForSession(sessionId)
                    transformRunDao.deleteRunsForSession(sessionId)
                    recordingSessionDao.deleteSession(sessionId)
                }.onSuccess {
                    selection.value =
                        selection.value.copy(
                            selectedSessionIds = selection.value.selectedSessionIds - sessionId,
                        )
                    _events.emit(HistoryEvent.Message("Record deleted"))
                }.onFailure {
                    _events.emit(HistoryEvent.Message(it.message ?: "Unable to delete record"))
                }
            }
        }

        fun saveAudioCopy(sessionId: String) {
            viewModelScope.launch {
                val session = recordingSessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                val source = File(session.audioFilePath)
                if (!source.exists()) {
                    _events.emit(HistoryEvent.Message("Audio file is no longer available"))
                    return@launch
                }

                val outputDir =
                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                        ?.resolve("saved-recordings")
                        ?: context.filesDir.resolve("saved-recordings")
                outputDir.mkdirs()

                val destination = uniqueFile(outputDir, source.name)
                runCatching { source.copyTo(destination) }
                    .onSuccess {
                        _events.emit(HistoryEvent.Message("Saved copy to ${destination.absolutePath}"))
                    }
                    .onFailure {
                        _events.emit(HistoryEvent.Message(it.message ?: "Unable to save copy"))
                    }
            }
        }

        fun setArchived(
            sessionId: String,
            archived: Boolean,
        ) {
            viewModelScope.launch {
                val session = recordingSessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                recordingSessionDao.updateSession(
                    session.copy(
                        isArchived = archived,
                        status = if (archived) SessionStatus.ARCHIVED.name else restoreStatus(session.status),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                _events.emit(HistoryEvent.Message(if (archived) "Record archived" else "Record restored"))
            }
        }

        fun transformWithDefaultProfile(sessionId: String) {
            viewModelScope.launch {
                val profileId = preferencesDataStore.defaultTransformProfileId.first()
                if (profileId == null) {
                    _events.emit(HistoryEvent.Message("Choose a default profile before transforming"))
                    return@launch
                }
                sessionTransformCoordinator.transformLatestRawTranscript(sessionId, profileId)
                    .onSuccess { _events.emit(HistoryEvent.Message("Transform completed")) }
                    .onFailure { _events.emit(HistoryEvent.Message(it.message ?: "Transform failed")) }
            }
        }

        fun retryTranscription(sessionId: String) {
            viewModelScope.launch {
                sessionTranscriptionCoordinator.transcribeSession(sessionId)
                    .onSuccess { _events.emit(HistoryEvent.Message("Transcription completed")) }
                    .onFailure { _events.emit(HistoryEvent.Message(it.message ?: "Transcription failed")) }
            }
        }

        fun resetTranscriptionState(sessionId: String) {
            viewModelScope.launch {
                val session = recordingSessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                recordingSessionDao.updateSession(
                    session.copy(
                        status = SessionStatus.RECORDED.name,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                _events.emit(HistoryEvent.Message("Transcription state cleared"))
            }
        }

        fun enterSelectionMode(sessionId: String) {
            selection.value = RecordsSelectionState(selectedSessionIds = setOf(sessionId))
        }

        fun toggleSelection(sessionId: String) {
            val selectedIds = selection.value.selectedSessionIds.toMutableSet()
            if (!selectedIds.add(sessionId)) {
                selectedIds.remove(sessionId)
            }
            selection.value = RecordsSelectionState(selectedSessionIds = selectedIds)
        }

        fun clearSelection() {
            selection.value = RecordsSelectionState()
        }

        fun selectAllVisible() {
            val visibleIds =
                (uiState.value as? HistoryUiState.Success)
                    ?.sessions
                    ?.map { it.session.id }
                    .orEmpty()
                    .toSet()
            selection.value = RecordsSelectionState(selectedSessionIds = visibleIds)
        }

        fun setArchivedForSelected(archived: Boolean) {
            viewModelScope.launch {
                val selectedIds = selection.value.selectedSessionIds
                if (selectedIds.isEmpty()) return@launch

                selectedIds.forEach { sessionId ->
                    recordingSessionDao.getSessionByIdOnce(sessionId)?.let { session ->
                        recordingSessionDao.updateSession(
                            session.copy(
                                isArchived = archived,
                                status = if (archived) SessionStatus.ARCHIVED.name else restoreStatus(session.status),
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                selection.value = RecordsSelectionState()
                _events.emit(
                    HistoryEvent.Message(
                        if (archived) "Archived ${selectedIds.size} records" else "Restored ${selectedIds.size} records",
                    ),
                )
            }
        }

        fun deleteSelectedSessions() {
            viewModelScope.launch {
                val selectedIds = selection.value.selectedSessionIds.toList()
                if (selectedIds.isEmpty()) return@launch

                var deletedCount = 0
                selectedIds.forEach { sessionId ->
                    val session = recordingSessionDao.getSessionByIdOnce(sessionId) ?: return@forEach
                    runCatching {
                        File(session.audioFilePath).takeIf { it.exists() }?.delete()
                        transcriptDao.deleteTranscriptsForSession(sessionId)
                        transformRunDao.deleteRunsForSession(sessionId)
                        recordingSessionDao.deleteSession(sessionId)
                    }.onSuccess {
                        deletedCount += 1
                    }
                }
                selection.value = RecordsSelectionState()
                _events.emit(HistoryEvent.Message("Deleted $deletedCount records"))
            }
        }

        fun transformSelectedSessions() {
            viewModelScope.launch {
                val selectedIds = selection.value.selectedSessionIds.toList()
                if (selectedIds.isEmpty()) return@launch

                val profileId = preferencesDataStore.defaultTransformProfileId.first()
                if (profileId == null) {
                    _events.emit(HistoryEvent.Message("Choose a default profile before transforming"))
                    return@launch
                }

                var completed = 0
                selectedIds.forEach { sessionId ->
                    sessionTransformCoordinator.transformLatestRawTranscript(sessionId, profileId)
                        .onSuccess { completed += 1 }
                }
                selection.value = RecordsSelectionState()
                _events.emit(HistoryEvent.Message("Transformed $completed of ${selectedIds.size} records"))
            }
        }

        fun transcribeSelectedSessions() {
            viewModelScope.launch {
                val selectedIds = selection.value.selectedSessionIds.toList()
                if (selectedIds.isEmpty()) return@launch

                var started = 0
                var skipped = 0
                selectedIds.forEach { sessionId ->
                    val session = recordingSessionDao.getSessionByIdOnce(sessionId)
                    val status =
                        session?.let {
                            runCatching { SessionStatus.valueOf(it.status) }.getOrNull()
                        }
                    when {
                        status == null -> skipped += 1
                        isEligibleForTranscription(status) -> {
                            sessionTranscriptionCoordinator.transcribeSession(sessionId)
                            started += 1
                        }
                        else -> skipped += 1
                    }
                }
                selection.value = RecordsSelectionState()
                val message =
                    buildString {
                        append("Queued $started of ${selectedIds.size} records for transcription")
                        if (skipped > 0) append(" ($skipped skipped — already transcribed or in progress)")
                    }
                _events.emit(HistoryEvent.Message(message))
            }
        }

        private fun comparatorFor(sortOption: RecordsSortOption): Comparator<RecordingSession> =
            when (sortOption) {
                RecordsSortOption.NEWEST -> compareByDescending<RecordingSession> { it.createdAt }
                RecordsSortOption.OLDEST -> compareBy<RecordingSession> { it.createdAt }
                RecordsSortOption.LONGEST -> compareByDescending<RecordingSession> { it.durationMs }
                RecordsSortOption.LARGEST -> compareByDescending<RecordingSession> { it.fileSizeBytes }
            }

        private fun matchesDateFilter(
            session: RecordingSession,
            dateRange: RecordsDateRange,
        ): Boolean {
            val sessionDate = session.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
            val today = LocalDate.now()
            return when (dateRange) {
                RecordsDateRange.ALL -> true
                RecordsDateRange.TODAY -> sessionDate == today
                RecordsDateRange.LAST_7_DAYS -> sessionDate >= today.minusDays(6)
                RecordsDateRange.LAST_30_DAYS -> sessionDate >= today.minusDays(29)
            }
        }

        private fun uniqueFile(
            directory: File,
            originalName: String,
        ): File {
            val baseName = originalName.substringBeforeLast('.')
            val extension = originalName.substringAfterLast('.', "")
            var candidate = directory.resolve(originalName)
            var index = 1
            while (candidate.exists()) {
                val numberedName =
                    if (extension.isBlank()) {
                        "$baseName-$index"
                    } else {
                        "$baseName-$index.$extension"
                    }
                candidate = directory.resolve(numberedName)
                index += 1
            }
            return candidate
        }

        private fun restoreStatus(status: String): String {
            val current = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.RECORDED)
            return if (current == SessionStatus.ARCHIVED) SessionStatus.RECORDED.name else current.name
        }
    }

internal fun isEligibleForTranscription(status: SessionStatus): Boolean = status == SessionStatus.RECORDED || status == SessionStatus.FAILED
