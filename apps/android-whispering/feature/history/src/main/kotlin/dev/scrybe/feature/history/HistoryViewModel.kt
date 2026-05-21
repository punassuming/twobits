package dev.scrybe.feature.history

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.common.TagsCodec
import dev.scrybe.core.common.TransformStepsCodec
import dev.scrybe.core.common.WaveformCodec
import dev.scrybe.core.common.sanitizeFileName
import dev.scrybe.core.database.FolderDao
import dev.scrybe.core.database.FolderEntity
import dev.scrybe.core.database.PersonDao
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.RecordingSessionEntity
import dev.scrybe.core.database.SessionTaskDao
import dev.scrybe.core.database.SpeakerSegmentDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TransformRunDao
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.localai.AutoRenameServiceFacade
import dev.scrybe.core.localai.ClusteringServiceFacade
import dev.scrybe.core.localai.SemanticSearchServiceFacade
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.Folder
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.TranscriptType
import dev.scrybe.core.model.TransformProfile
import dev.scrybe.core.transcription.SessionTranscriptionCoordinator
import dev.scrybe.core.transforms.SessionSummary
import dev.scrybe.core.transforms.SessionTransformCoordinator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class TransformDialogResult(
    val profileName: String,
    val text: String,
)

data class TransformDialogState(
    val sessionIds: List<String> = emptyList(),
    val sessionTitles: List<String> = emptyList(),
    val runningProfileId: String? = null,
    val result: TransformDialogResult? = null,
)

sealed interface HistoryEvent {
    data class Message(
        val text: String,
    ) : HistoryEvent

    data class ShareText(
        val title: String,
        val text: String,
    ) : HistoryEvent

    data class TransformResult(
        val profileName: String,
        val text: String,
    ) : HistoryEvent
}

private data class HistoryUiInputs(
    val query: String,
    val filters: RecordsFilterState,
    val confirmSwipeActions: Boolean,
    val showRecordingInfoInList: Boolean,
    val selection: RecordsSelectionState,
)

private data class FolderNavState(
    val currentFolderId: String?,
    val allFolders: List<Folder>,
    val speakerPersonNames: Map<String, List<String>> = emptyMap(),
)

@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val recordingSessionDao: RecordingSessionDao,
        private val sessionTaskDao: SessionTaskDao,
        private val transcriptDao: TranscriptDao,
        private val transformRunDao: TransformRunDao,
        private val transformProfileDao: TransformProfileDao,
        private val folderDao: FolderDao,
        private val speakerSegmentDao: SpeakerSegmentDao,
        private val personDao: PersonDao,
        private val preferencesDataStore: AppPreferencesDataStore,
        private val sessionTransformCoordinator: SessionTransformCoordinator,
        private val sessionTranscriptionCoordinator: SessionTranscriptionCoordinator,
        private val clusteringService: ClusteringServiceFacade,
        private val autoRenameService: AutoRenameServiceFacade,
        private val semanticSearchService: SemanticSearchServiceFacade,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val filters = MutableStateFlow(RecordsFilterState())
        private val selection = MutableStateFlow(RecordsSelectionState())
        private val transformingSessionIds = MutableStateFlow<Set<String>>(emptySet())
        private val _isAiWorking = MutableStateFlow(false)
        val isAiWorking: StateFlow<Boolean> = _isAiWorking.asStateFlow()
        private val currentFolderId = MutableStateFlow<String?>(null)
        private val _events = MutableSharedFlow<HistoryEvent>()
        val events = _events.asSharedFlow()
        private val _transformDialog = MutableStateFlow<TransformDialogState?>(null)
        val transformDialog: StateFlow<TransformDialogState?> = _transformDialog.asStateFlow()
        private val semanticSearchLoading = MutableStateFlow(false)
        private val semanticRankedIds = MutableStateFlow<List<String>?>(null)
        val profiles: StateFlow<List<TransformProfile>> =
            transformProfileDao
                .getAllProfiles()
                .map { entities ->
                    entities.map { entity ->
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
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val historyUiInputs =
            combine(
                query,
                filters,
                preferencesDataStore.confirmRecordSwipeActions,
                preferencesDataStore.showRecordingInfoInList,
                selection,
            ) { queryValue, filterState, confirmSwipeActions, showRecordingInfoInList, selectionState ->
                HistoryUiInputs(
                    query = queryValue,
                    filters = filterState,
                    confirmSwipeActions = confirmSwipeActions,
                    showRecordingInfoInList = showRecordingInfoInList,
                    selection = selectionState,
                )
            }

        private val folderNavState =
            combine(
                currentFolderId,
                folderDao.getAllFolders(),
                combine(
                    speakerSegmentDao.getAllSegmentsWithPerson(),
                    personDao.getAllPersons(),
                ) { segments, persons ->
                    val personMap = persons.associate { it.id to it.name }
                    segments
                        .groupBy({ it.sessionId }) { seg -> personMap[seg.personId].orEmpty() }
                        .mapValues { (_, names) -> names.filter { it.isNotBlank() }.distinct() }
                },
            ) { folderId, entities, speakerPersonNames ->
                FolderNavState(
                    currentFolderId = folderId,
                    allFolders =
                        entities.map { entity ->
                            Folder(
                                id = entity.id,
                                name = entity.name,
                                parentFolderId = entity.parentFolderId,
                                createdAt = Instant.ofEpochMilli(entity.createdAt),
                            )
                        },
                    speakerPersonNames = speakerPersonNames,
                )
            }

        init {
            viewModelScope.launch {
                recordingSessionDao.updateSessionsByStatus(
                    oldStatus = SessionStatus.TRANSCRIBING.name,
                    newStatus = SessionStatus.FAILED.name,
                    updatedAt = System.currentTimeMillis(),
                )
                recoverOrphanedRecordings()
            }
        }

        private val openTaskCountsPerSession =
            sessionTaskDao.getOpenTaskCountsPerSession()

        private val baseUiState =
            combine(
                recordingSessionDao.getAllSessions(),
                transcriptDao.getAllTranscripts(),
                historyUiInputs,
                transformingSessionIds,
                combine(folderNavState, openTaskCountsPerSession) { nav, counts -> nav to counts },
            ) { entities, transcripts, inputs, currentlyTransforming, folderNavAndCounts ->
                val folderNav = folderNavAndCounts.first
                val taskCounts = folderNavAndCounts.second
                val speakerPersonNames = folderNav.speakerPersonNames
                val taskCountMap = taskCounts.associate { it.sessionId to it.count }
                val sessions =
                    entities.map { entity ->
                        RecordingSession(
                            id = entity.id,
                            title = entity.title,
                            tags = TagsCodec.decode(entity.tags),
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
                            folderId = entity.folderId,
                            locationLat = entity.locationLat,
                            locationLng = entity.locationLng,
                            locationLabel = entity.locationLabel,
                            sentimentJson = entity.sentimentJson,
                            topicsJson = entity.topicsJson,
                            mode = RecordingMode.valueOf(entity.mode),
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
                        .filter { session -> session.folderId == folderNav.currentFolderId }
                        .filter { session -> matchesDateFilter(session, inputs.filters.dateRange) }
                        .filter { session ->
                            inputs.filters.includedStatuses.isEmpty() || session.status in inputs.filters.includedStatuses
                        }.filter { session ->
                            inputs.filters.selectedTag == null || inputs.filters.selectedTag in session.tags
                        }.filter { session ->
                            inputs.filters.selectedMode == null || session.mode == inputs.filters.selectedMode
                        }.filter { session ->
                            if (inputs.query.isBlank()) {
                                true
                            } else {
                                val searchTerm = inputs.query.trim().lowercase()
                                session.title.lowercase().contains(searchTerm) ||
                                    session.tags.any { it.lowercase().contains(searchTerm) } ||
                                    session.status.name
                                        .lowercase()
                                        .contains(searchTerm) ||
                                    latestTranscriptBySession[session.id]
                                        .orEmpty()
                                        .lowercase()
                                        .contains(searchTerm) ||
                                    session.locationLabel?.lowercase()?.contains(searchTerm) == true ||
                                    speakerPersonNames[session.id]
                                        .orEmpty()
                                        .any { it.lowercase().contains(searchTerm) }
                            }
                        }.sortedWith(comparatorFor(inputs.filters.sortOption))
                        .map { session ->
                            HistorySessionItem(
                                session = session,
                                transcriptPreview = latestTranscriptBySession[session.id],
                                speakerCount = speakerPersonNames[session.id]?.size ?: 0,
                                openTaskCount = taskCountMap[session.id] ?: 0,
                            )
                        }

                val subfolders =
                    folderNav.allFolders
                        .filter { it.parentFolderId == folderNav.currentFolderId }
                        .sortedBy { it.name.lowercase() }
                val breadcrumb = buildBreadcrumb(folderNav.currentFolderId, folderNav.allFolders)

                val sessionsByFolderId: Map<String, List<HistorySessionItem>> =
                    sessions
                        .filter { session -> session.isArchived == inputs.filters.showArchived }
                        .filter { session -> session.folderId != null }
                        .filter { session -> matchesDateFilter(session, inputs.filters.dateRange) }
                        .filter { session ->
                            inputs.filters.includedStatuses.isEmpty() ||
                                session.status in inputs.filters.includedStatuses
                        }.filter { session ->
                            if (inputs.query.isBlank()) {
                                true
                            } else {
                                val searchTerm = inputs.query.trim().lowercase()
                                session.title.lowercase().contains(searchTerm) ||
                                    session.tags.any { it.lowercase().contains(searchTerm) } ||
                                    session.status.name
                                        .lowercase()
                                        .contains(searchTerm) ||
                                    latestTranscriptBySession[session.id]
                                        .orEmpty()
                                        .lowercase()
                                        .contains(searchTerm) ||
                                    session.locationLabel?.lowercase()?.contains(searchTerm) == true ||
                                    speakerPersonNames[session.id]
                                        .orEmpty()
                                        .any { it.lowercase().contains(searchTerm) }
                            }
                        }.filter { session ->
                            inputs.filters.selectedTag == null || inputs.filters.selectedTag in session.tags
                        }.groupBy { session -> session.folderId!! }
                        .mapValues { (_, folderSessions) ->
                            folderSessions
                                .sortedWith(comparatorFor(inputs.filters.sortOption))
                                .map { session ->
                                    HistorySessionItem(
                                        session = session,
                                        transcriptPreview = latestTranscriptBySession[session.id],
                                        speakerCount = speakerPersonNames[session.id]?.size ?: 0,
                                        openTaskCount = taskCountMap[session.id] ?: 0,
                                    )
                                }
                        }

                val availableTags =
                    sessions
                        .filter { session -> session.isArchived == inputs.filters.showArchived }
                        .flatMap { it.tags }
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .map { it.key to it.value }

                val allSelectableIds =
                    (filteredSessions + sessionsByFolderId.values.flatten())
                        .map { it.session.id }
                        .toSet()
                HistoryUiState.Success(
                    sessions = filteredSessions,
                    filters = inputs.filters,
                    interactionPreferences =
                        RecordsInteractionPreferences(
                            confirmSwipeActions = inputs.confirmSwipeActions,
                            showRecordingInfoInList = inputs.showRecordingInfoInList,
                        ),
                    selection =
                        inputs.selection.copy(
                            selectedSessionIds =
                                inputs.selection.selectedSessionIds.intersect(allSelectableIds),
                        ),
                    transformingSessionIds = currentlyTransforming,
                    currentFolderId = folderNav.currentFolderId,
                    subfolders = subfolders,
                    breadcrumb = breadcrumb,
                    allFolders = folderNav.allFolders,
                    sessionsByFolderId = sessionsByFolderId,
                    availableTags = availableTags,
                ) as HistoryUiState
            }

        val uiState: StateFlow<HistoryUiState> =
            combine(
                baseUiState,
                semanticSearchLoading,
                semanticRankedIds,
                sessionTaskDao.getAllOpenTaskCount(),
                sessionTaskDao.getSessionsWithOpenTaskCount(),
            ) { base, semanticLoading, semanticIds, openTaskCount, sessionsWithTasks ->
                if (base !is HistoryUiState.Success) return@combine base
                val rankedSessions =
                    if (semanticIds != null) {
                        val idIndex = semanticIds.withIndex().associate { (i, id) -> id to i }
                        base.sessions.sortedBy { item ->
                            idIndex[item.session.id] ?: Int.MAX_VALUE
                        }
                    } else {
                        base.sessions
                    }
                base.copy(
                    sessions = rankedSessions,
                    semanticSearchLoading = semanticLoading,
                    semanticRankedIds = semanticIds,
                    openTaskCount = openTaskCount,
                    sessionsWithTasksCount = sessionsWithTasks,
                )
            }.catch { emit(HistoryUiState.Error(it.message ?: "Unknown error")) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = HistoryUiState.Loading,
                )

        val folderTree: StateFlow<List<FolderNode>> =
            folderNavState
                .map { nav ->
                    buildFolderTree(nav.allFolders)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun updateQuery(value: String) {
            query.value = value
            if (semanticRankedIds.value != null) semanticRankedIds.value = null
        }

        fun updateFilters(next: RecordsFilterState) {
            filters.value = next
            selection.value = RecordsSelectionState()
        }

        fun selectTag(tag: String?) {
            filters.value = filters.value.copy(selectedTag = tag)
        }

        fun selectMode(mode: RecordingMode?) {
            filters.value = filters.value.copy(selectedMode = mode)
        }

        fun triggerSemanticSearch(query: String) {
            if (query.isBlank()) return
            viewModelScope.launch {
                semanticSearchLoading.value = true
                val summaries = buildSessionSummaries(filterIds = null)
                semanticSearchService
                    .rankByRelevance(query, summaries)
                    .onSuccess { rankedIds -> semanticRankedIds.value = rankedIds }
                    .onFailure { _events.emit(HistoryEvent.Message(it.message ?: "AI search failed")) }
                semanticSearchLoading.value = false
            }
        }

        fun clearSemanticSearch() {
            semanticRankedIds.value = null
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
                    deleteAudioFileIfSafe(session.audioFilePath)
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

        fun shareTranscript(sessionId: String) {
            viewModelScope.launch {
                val session = recordingSessionDao.getSessionByIdOnce(sessionId)
                if (session == null) {
                    _events.emit(HistoryEvent.Message("Recording not found"))
                    return@launch
                }
                val transcripts = transcriptDao.getTranscriptsForSession(sessionId).first()
                val priorityOrder =
                    listOf(
                        TranscriptType.EDITED.name,
                        TranscriptType.TRANSFORMED.name,
                        TranscriptType.RAW.name,
                    )
                val transcriptsByType = transcripts.groupBy { it.type }
                val transcript =
                    priorityOrder
                        .firstNotNullOfOrNull { type -> transcriptsByType[type]?.maxByOrNull { it.createdAt } }
                if (transcript == null) {
                    _events.emit(HistoryEvent.Message("No transcript available to share"))
                } else {
                    _events.emit(HistoryEvent.ShareText(title = session.title, text = transcript.content))
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
                    context
                        .getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                        ?.resolve("saved-recordings")
                        ?: context.filesDir.resolve("saved-recordings")
                outputDir.mkdirs()

                val destination = uniqueFile(outputDir, "${sanitizeFileName(session.title)}.${source.extension}")
                runCatching { source.copyTo(destination) }
                    .onSuccess {
                        _events.emit(HistoryEvent.Message("Saved copy to ${destination.absolutePath}"))
                    }.onFailure {
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
                transformingSessionIds.value = transformingSessionIds.value + sessionId
                sessionTransformCoordinator
                    .transformLatestRawTranscript(sessionId, profileId)
                    .onSuccess { transcript ->
                        _events.emit(
                            HistoryEvent.TransformResult(
                                profileName = "Transform",
                                text = transcript.content,
                            ),
                        )
                    }.onFailure { _events.emit(HistoryEvent.Message(it.message ?: "Transform failed")) }
                transformingSessionIds.value = transformingSessionIds.value - sessionId
            }
        }

        fun openTransformDialog(sessionIds: List<String>) {
            if (sessionIds.isEmpty()) return
            val state = uiState.value
            val allItems =
                if (state is HistoryUiState.Success) {
                    state.sessions + state.sessionsByFolderId.values.flatten()
                } else {
                    emptyList()
                }
            val titles = sessionIds.mapNotNull { id -> allItems.find { it.session.id == id }?.session?.title }
            _transformDialog.value = TransformDialogState(sessionIds = sessionIds, sessionTitles = titles)
        }

        fun runTransformFromDialog(profile: TransformProfile) {
            val dialog = _transformDialog.value ?: return
            _transformDialog.value = dialog.copy(runningProfileId = profile.id, result = null)
            viewModelScope.launch {
                val sessionIds = dialog.sessionIds
                val outcome =
                    if (sessionIds.size == 1) {
                        sessionTransformCoordinator
                            .transformLatestRawTranscript(sessionIds.first(), profile.id)
                            .map { it.content }
                    } else {
                        sessionTransformCoordinator
                            .transformCombinedLatestTranscripts(sessionIds, profile.id)
                            .map { it.transcript.content }
                    }
                val dialogResult = outcome.getOrNull()?.let { TransformDialogResult(profile.name, it) }
                _transformDialog.value =
                    _transformDialog.value?.copy(
                        runningProfileId = null,
                        result = dialogResult,
                    )
                outcome.onFailure { _events.emit(HistoryEvent.Message(it.message ?: "Transform failed")) }
            }
        }

        fun closeTransformDialog() {
            _transformDialog.value = null
        }

        fun retryTranscription(sessionId: String) {
            viewModelScope.launch {
                sessionTranscriptionCoordinator
                    .transcribeSession(sessionId)
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
                        deleteAudioFileIfSafe(session.audioFilePath)
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

                val message =
                    if (selectedIds.size == 1) {
                        sessionTransformCoordinator
                            .transformLatestRawTranscript(selectedIds.first(), profileId)
                            .fold(
                                onSuccess = { "Transform completed" },
                                onFailure = { it.message ?: "Transform failed" },
                            )
                    } else {
                        sessionTransformCoordinator
                            .transformCombinedLatestTranscripts(selectedIds, profileId)
                            .fold(
                                onSuccess = { result ->
                                    buildString {
                                        append("Consolidated ${result.includedSessionCount} transcripts into ")
                                        append(result.anchorSessionTitle)
                                        if (result.skippedSessionCount > 0) {
                                            append(" (${result.skippedSessionCount} skipped without transcripts)")
                                        }
                                    }
                                },
                                onFailure = { it.message ?: "Consolidation failed" },
                            )
                    }
                selection.value = RecordsSelectionState()
                _events.emit(HistoryEvent.Message(message))
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

        fun importRecording(uri: Uri) {
            viewModelScope.launch {
                runCatching {
                    val recordingsDir =
                        context.filesDir.resolve("recordings").apply { mkdirs() }
                    val mimeType = context.contentResolver.getType(uri)
                    val ext = mimeExtension(mimeType)
                    val fileName = "recording_${UUID.randomUUID()}.$ext"
                    val destination = File(recordingsDir, fileName)

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destination.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: error("Failed to open input stream for selected file")

                    createSessionFromFile(destination)
                }.onSuccess {
                    _events.emit(HistoryEvent.Message("Recording imported"))
                }.onFailure {
                    _events.emit(
                        HistoryEvent.Message(
                            it.message ?: "Unable to import recording",
                        ),
                    )
                }
            }
        }

        private suspend fun recoverOrphanedRecordings() {
            val recordingsDir = context.filesDir.resolve("recordings")
            if (!recordingsDir.exists()) return
            cleanupBrokenRecordingFiles(recordingsDir)

            val knownPaths =
                recordingSessionDao.getAllAudioFilePaths().toSet()
            val audioExtensions = setOf("m4a", "mp4", "ogg", "webm")
            val orphanedFiles =
                recordingsDir
                    .listFiles()
                    ?.filter { file ->
                        file.isFile &&
                            file.extension.lowercase() in audioExtensions &&
                            hasAudioPayload(file) &&
                            file.absolutePath !in knownPaths
                    }.orEmpty()

            if (orphanedFiles.isEmpty()) return

            var recovered = 0
            orphanedFiles.forEach { file ->
                runCatching { createSessionFromFile(file) }
                    .onSuccess { recovered++ }
                    .onFailure { e ->
                        android.util.Log.w(
                            TAG,
                            "Failed to recover ${file.name}: ${e.message}",
                        )
                    }
            }
            if (recovered > 0) {
                _events.emit(
                    HistoryEvent.Message(
                        "Recovered $recovered recording(s) from disk",
                    ),
                )
            }
        }

        private suspend fun createSessionFromFile(file: File) {
            if (!hasAudioPayload(file)) {
                runCatching { file.delete() }
                return
            }
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val durationMs =
                    retriever
                        .extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION,
                        )?.toLongOrNull() ?: 0L
                val sampleRate =
                    retriever
                        .extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_SAMPLERATE,
                        )?.toIntOrNull() ?: DEFAULT_SAMPLE_RATE
                val bitrate =
                    retriever
                        .extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_BITRATE,
                        )?.toIntOrNull() ?: DEFAULT_BIT_RATE
                val channelCount =
                    runCatching {
                        val extractor = MediaExtractor()
                        try {
                            extractor.setDataSource(file.absolutePath)
                            val audioTrackIndex =
                                (0 until extractor.trackCount).firstOrNull { trackIndex ->
                                    extractor
                                        .getTrackFormat(trackIndex)
                                        .getString(MediaFormat.KEY_MIME)
                                        ?.startsWith("audio/") == true
                                }
                            if (audioTrackIndex != null) {
                                extractor
                                    .getTrackFormat(audioTrackIndex)
                                    .getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            } else {
                                1
                            }
                        } finally {
                            extractor.release()
                        }
                    }.onFailure { e ->
                        android.util.Log.w(TAG, "Failed to extract channel count from ${file.name}: ${e.message}")
                    }.getOrDefault(1)

                val audioFormat = audioFormatFromExtension(file.extension)
                val createdAt = file.lastModified()

                if (recordingSessionDao.getSessionByAudioFilePath(file.absolutePath) != null) return

                val formattedTimestamp =
                    TITLE_FORMAT.format(Date(createdAt))
                val sessionId = UUID.randomUUID().toString()

                recordingSessionDao.insertSession(
                    RecordingSessionEntity(
                        id = sessionId,
                        title = "Recording $formattedTimestamp",
                        tags = "",
                        audioFilePath = file.absolutePath,
                        durationMs = durationMs,
                        fileSizeBytes = file.length(),
                        audioFormat = audioFormat.name,
                        sampleRateHz = sampleRate,
                        encodingBitRate = bitrate,
                        channelCount = channelCount,
                        waveformSamples = "",
                        status = SessionStatus.RECORDED.name,
                        isArchived = false,
                        estimatedTranscriptionCostUsd = null,
                        createdAt = createdAt,
                        updatedAt = createdAt,
                    ),
                )
            } finally {
                retriever.release()
            }
        }

        private suspend fun deleteAudioFileIfSafe(audioFilePath: String) {
            if (recordingSessionDao.countSessionsByAudioFilePath(audioFilePath) > 1) {
                return
            }
            File(audioFilePath).takeIf { it.exists() }?.delete()
        }

        private fun cleanupBrokenRecordingFiles(recordingsDir: File) {
            recordingsDir
                .listFiles()
                ?.filter { it.isFile && !hasAudioPayload(it) }
                .orEmpty()
                .forEach { file ->
                    runCatching { file.delete() }
                        .onFailure { error ->
                            android.util.Log.w(
                                TAG,
                                "Failed to delete zero-byte recording ${file.name}: ${error.message}",
                            )
                        }
                }
        }

        private fun hasAudioPayload(file: File): Boolean = file.isFile && file.length() > 0L

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

        private fun isFolderDescendant(
            ancestorId: String,
            targetId: String,
            allFolders: List<FolderEntity>,
        ): Boolean {
            var current: String? = targetId
            while (current != null) {
                if (current == ancestorId) return true
                current = allFolders.find { it.id == current }?.parentFolderId
            }
            return false
        }

        private fun restoreStatus(status: String): String {
            val current = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.RECORDED)
            return if (current == SessionStatus.ARCHIVED) SessionStatus.RECORDED.name else current.name
        }

        fun navigateToFolder(folderId: String?) {
            currentFolderId.value = folderId
            selection.value = RecordsSelectionState()
        }

        fun navigateUp() {
            viewModelScope.launch {
                val current = currentFolderId.value ?: return@launch
                val folder = folderDao.getFolderById(current)
                currentFolderId.value = folder?.parentFolderId
                selection.value = RecordsSelectionState()
            }
        }

        fun createFolder(
            name: String,
            parentFolderId: String? = null,
        ) {
            viewModelScope.launch {
                val folder =
                    FolderEntity(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        parentFolderId = parentFolderId,
                        createdAt = System.currentTimeMillis(),
                    )
                folderDao.insertFolder(folder)
                _events.emit(HistoryEvent.Message("Folder created"))
            }
        }

        fun renameFolder(
            folderId: String,
            newName: String,
        ) {
            viewModelScope.launch {
                val folder = folderDao.getFolderById(folderId) ?: return@launch
                folderDao.updateFolder(folder.copy(name = newName.trim()))
                _events.emit(HistoryEvent.Message("Folder renamed"))
            }
        }

        fun deleteFolder(folderId: String) {
            viewModelScope.launch {
                recordingSessionDao.moveSessionsToFolder(
                    sessionIds = recordingSessionDao.getSessionsByFolder(folderId).first().map { it.id },
                    folderId = null,
                    updatedAt = System.currentTimeMillis(),
                )
                folderDao.deleteFolder(folderId)
                _events.emit(HistoryEvent.Message("Folder deleted"))
            }
        }

        fun moveFolderToParent(
            folderId: String,
            newParentId: String?,
        ) {
            viewModelScope.launch {
                val folder = folderDao.getFolderById(folderId) ?: return@launch
                if (newParentId == folderId) return@launch
                if (newParentId != null) {
                    val allFolders = folderDao.getAllFoldersOnce()
                    if (isFolderDescendant(ancestorId = folderId, targetId = newParentId, allFolders = allFolders)) return@launch
                }
                folderDao.updateFolder(folder.copy(parentFolderId = newParentId))
                _events.emit(HistoryEvent.Message("Folder moved"))
            }
        }

        fun moveSessionsToFolder(
            sessionIds: List<String>,
            folderId: String?,
        ) {
            viewModelScope.launch {
                recordingSessionDao.moveSessionsToFolder(
                    sessionIds = sessionIds,
                    folderId = folderId,
                    updatedAt = System.currentTimeMillis(),
                )
                selection.value = RecordsSelectionState()
                val label = if (folderId != null) "Moved to folder" else "Removed from folder"
                _events.emit(HistoryEvent.Message(label))
            }
        }

        fun suggestAndApplyClusters(limitToSessionIds: Set<String>? = null) {
            viewModelScope.launch {
                _isAiWorking.value = true
                val msg =
                    if (limitToSessionIds != null) {
                        "Analyzing ${limitToSessionIds.size} selected recordings…"
                    } else {
                        "Analyzing recordings…"
                    }
                _events.emit(HistoryEvent.Message(msg))
                val summaries = buildSessionSummaries(limitToSessionIds)
                if (summaries.isEmpty()) {
                    _events.emit(HistoryEvent.Message("No recordings to organize"))
                    _isAiWorking.value = false
                    return@launch
                }
                val existingFolders = folderDao.getAllFoldersOnce()
                clusteringService
                    .suggestClusters(summaries, existingFolders.map { it.name }, buildCommonTags(summaries))
                    .onSuccess { clusters -> applyClusterResults(clusters, existingFolders) }
                    .onFailure { _events.emit(HistoryEvent.Message(it.message ?: "Clustering failed")) }
                _isAiWorking.value = false
            }
        }

        private suspend fun buildSessionSummaries(filterIds: Set<String>?): List<SessionSummary> {
            val allSessions = recordingSessionDao.getAllSessionsOnce()
            val transcripts = transcriptDao.getAllTranscriptsOnce()
            val latestBySession =
                transcripts
                    .groupBy { it.sessionId }
                    .mapValues { (_, items) -> items.maxByOrNull { it.createdAt }?.content }
            return allSessions
                .filter { !it.isArchived }
                .filter { filterIds == null || it.id in filterIds }
                .map { entity ->
                    SessionSummary(
                        id = entity.id,
                        title = entity.title,
                        tags = TagsCodec.decode(entity.tags),
                        transcriptPreview = latestBySession[entity.id],
                    )
                }
        }

        private fun buildCommonTags(summaries: List<SessionSummary>): List<String> =
            summaries
                .flatMap { it.tags }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(20)
                .map { it.key }

        private suspend fun applyClusterResults(
            clusters: List<dev.scrybe.core.transforms.ClusterSuggestion>,
            existingFolders: List<FolderEntity>,
        ) {
            var createdFolders = 0
            var movedSessions = 0
            clusters.forEach { cluster ->
                val existingFolder =
                    existingFolders.find { it.name.equals(cluster.folderName, ignoreCase = true) }
                val folderId =
                    if (existingFolder != null) {
                        existingFolder.id
                    } else {
                        val newId = UUID.randomUUID().toString()
                        folderDao.insertFolder(
                            FolderEntity(
                                id = newId,
                                name = cluster.folderName,
                                parentFolderId = currentFolderId.value,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                        createdFolders++
                        newId
                    }
                recordingSessionDao.moveSessionsToFolder(
                    sessionIds = cluster.sessionIds,
                    folderId = folderId,
                    updatedAt = System.currentTimeMillis(),
                )
                movedSessions += cluster.sessionIds.size
            }
            _events.emit(
                HistoryEvent.Message(
                    "Organized $movedSessions recordings into ${clusters.size} folders" +
                        if (createdFolders > 0) " ($createdFolders new)" else "",
                ),
            )
        }

        fun saveTags(
            sessionId: String,
            tagsInput: String,
        ) {
            val normalizedTags = TagsCodec.normalizeInput(tagsInput)
            viewModelScope.launch {
                val session = recordingSessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                recordingSessionDao.updateSession(
                    session.copy(
                        tags = TagsCodec.encode(normalizedTags),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

        suspend fun suggestTitleForSession(sessionId: String): String? {
            val session = recordingSessionDao.getSessionByIdOnce(sessionId) ?: return null
            val transcripts = transcriptDao.getTranscriptsForSession(sessionId).first()
            val transcriptText = transcripts.maxByOrNull { it.createdAt }?.content ?: return null
            return autoRenameService.suggestTitle(transcriptText, session.title).getOrNull()
        }

        fun autoRenameSession(sessionId: String) {
            viewModelScope.launch {
                transformingSessionIds.value = transformingSessionIds.value + sessionId
                autoRenameSessionInternal(sessionId)
                    .onSuccess { _events.emit(HistoryEvent.Message("Recording renamed")) }
                    .onFailure { _events.emit(HistoryEvent.Message(it.message ?: "Could not suggest a title")) }
                transformingSessionIds.value = transformingSessionIds.value - sessionId
            }
        }

        fun autoRenameSelectedSessions() {
            viewModelScope.launch {
                val selectedIds = selection.value.selectedSessionIds.toList()
                if (selectedIds.isEmpty()) return@launch
                _isAiWorking.value = true
                var renamed = 0
                var failed = 0
                selectedIds.forEach { sessionId ->
                    autoRenameSessionInternal(sessionId)
                        .onSuccess { renamed++ }
                        .onFailure { failed++ }
                }
                selection.value = RecordsSelectionState()
                _isAiWorking.value = false
                _events.emit(
                    HistoryEvent.Message(
                        "Renamed $renamed recording(s)" +
                            if (failed > 0) ", $failed failed" else "",
                    ),
                )
            }
        }

        private suspend fun autoRenameSessionInternal(sessionId: String): Result<Unit> =
            runCatching {
                val session =
                    recordingSessionDao.getSessionByIdOnce(sessionId)
                        ?: error("Recording not found")
                val transcripts = transcriptDao.getTranscriptsForSession(sessionId).first()
                val transcriptText =
                    transcripts.maxByOrNull { it.createdAt }?.content
                        ?: error("No transcript available for renaming")
                val newTitle = autoRenameService.suggestTitle(transcriptText, session.title).getOrThrow()
                recordingSessionDao.updateSession(
                    session.copy(title = newTitle, updatedAt = System.currentTimeMillis()),
                )
            }

        companion object {
            private const val TAG = "HistoryViewModel"
            private val TITLE_FORMAT =
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            private const val DEFAULT_SAMPLE_RATE = 48_000
            private const val DEFAULT_BIT_RATE = 128_000
        }
    }

internal fun audioFormatFromExtension(ext: String): AudioFormat =
    when (ext.lowercase()) {
        "m4a" -> AudioFormat.AAC
        "mp4" -> AudioFormat.MP4
        "ogg" -> AudioFormat.OGG
        "webm" -> AudioFormat.WEBM
        else -> AudioFormat.AAC
    }

private fun mimeExtension(mimeType: String?): String =
    when (mimeType?.lowercase()) {
        "audio/mp4", "audio/aac", "audio/x-m4a" -> "m4a"
        "audio/ogg" -> "ogg"
        "audio/webm" -> "webm"
        else -> "m4a"
    }

private fun buildBreadcrumb(
    currentFolderId: String?,
    allFolders: List<Folder>,
): List<Folder> {
    if (currentFolderId == null) return emptyList()
    val trail = mutableListOf<Folder>()
    var folderId: String? = currentFolderId
    while (folderId != null) {
        val folder = allFolders.find { it.id == folderId } ?: break
        trail.add(0, folder)
        folderId = folder.parentFolderId
    }
    return trail
}

internal fun buildFolderTree(allFolders: List<Folder>): List<FolderNode> {
    val result = mutableListOf<FolderNode>()
    fun visit(parentId: String?, depth: Int) {
        allFolders
            .filter { it.parentFolderId == parentId }
            .sortedBy { it.name.lowercase() }
            .forEach { folder ->
                result.add(FolderNode(id = folder.id, name = folder.name, sessionCount = 0, depth = depth))
                visit(folder.id, depth + 1)
            }
    }
    visit(null, 0)
    return result
}

internal fun isEligibleForTranscription(status: SessionStatus): Boolean =
    status == SessionStatus.RECORDED ||
        status == SessionStatus.FAILED ||
        status == SessionStatus.PARTIAL_TRANSCRIPTION
