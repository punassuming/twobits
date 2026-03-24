package dev.scrybe.feature.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.common.WaveformCodec
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TransformRunDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HistoryEvent {
    data class Message(val text: String) : HistoryEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingSessionDao: RecordingSessionDao,
    private val transcriptDao: TranscriptDao,
    private val transformRunDao: TransformRunDao,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(RecordsFilterState())
    private val _events = MutableSharedFlow<HistoryEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<HistoryUiState> = combine(
        recordingSessionDao.getAllSessions(),
        transcriptDao.getAllTranscripts(),
        query,
        filters,
    ) { entities, transcripts, query, filters ->
        val sessions = entities.map { entity ->
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
                createdAt = Instant.ofEpochMilli(entity.createdAt),
                updatedAt = Instant.ofEpochMilli(entity.updatedAt),
            )
        }
        val latestTranscriptBySession = transcripts
            .groupBy { it.sessionId }
            .mapValues { (_, items) -> items.maxByOrNull { it.createdAt }?.content.orEmpty() }

        val filteredSessions = sessions
            .filter { session -> matchesDateFilter(session, filters.dateRange) }
            .filter { session ->
                filters.includedStatuses.isEmpty() || session.status in filters.includedStatuses
            }
            .filter { session ->
                if (query.isBlank()) {
                    true
                } else {
                    val searchTerm = query.trim().lowercase()
                    session.title.lowercase().contains(searchTerm) ||
                        session.status.name.lowercase().contains(searchTerm) ||
                        latestTranscriptBySession[session.id].orEmpty().lowercase().contains(searchTerm)
                }
            }
            .sortedWith(comparatorFor(filters.sortOption))
            .map { session ->
                HistorySessionItem(
                    session = session,
                    transcriptPreview = latestTranscriptBySession[session.id],
                )
            }

        HistoryUiState.Success(
            sessions = filteredSessions,
            filters = filters,
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
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            val session = recordingSessionDao.getSessionByIdOnce(sessionId) ?: return@launch
            val trimmedTitle = newTitle.trim()
            if (trimmedTitle.isBlank()) return@launch
            recordingSessionDao.updateSession(
                session.copy(
                    title = trimmedTitle,
                    updatedAt = System.currentTimeMillis(),
                )
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

            val outputDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
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

    private fun comparatorFor(sortOption: RecordsSortOption): Comparator<RecordingSession> = when (sortOption) {
        RecordsSortOption.NEWEST -> compareByDescending<RecordingSession> { it.createdAt }
        RecordsSortOption.OLDEST -> compareBy<RecordingSession> { it.createdAt }
        RecordsSortOption.LONGEST -> compareByDescending<RecordingSession> { it.durationMs }
        RecordsSortOption.LARGEST -> compareByDescending<RecordingSession> { it.fileSizeBytes }
    }

    private fun matchesDateFilter(session: RecordingSession, dateRange: RecordsDateRange): Boolean {
        val sessionDate = session.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (dateRange) {
            RecordsDateRange.ALL -> true
            RecordsDateRange.TODAY -> sessionDate == today
            RecordsDateRange.LAST_7_DAYS -> sessionDate >= today.minusDays(6)
            RecordsDateRange.LAST_30_DAYS -> sessionDate >= today.minusDays(29)
        }
    }

    private fun uniqueFile(directory: File, originalName: String): File {
        val baseName = originalName.substringBeforeLast('.')
        val extension = originalName.substringAfterLast('.', "")
        var candidate = directory.resolve(originalName)
        var index = 1
        while (candidate.exists()) {
            val numberedName = if (extension.isBlank()) {
                "$baseName-$index"
            } else {
                "$baseName-$index.$extension"
            }
            candidate = directory.resolve(numberedName)
            index += 1
        }
        return candidate
    }
}
