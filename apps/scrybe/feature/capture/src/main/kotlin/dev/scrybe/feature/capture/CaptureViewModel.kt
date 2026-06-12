package dev.scrybe.feature.capture

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.audio.AudioRecorder
import dev.scrybe.core.common.TagsCodec
import dev.scrybe.core.common.TransformStepsCodec
import dev.scrybe.core.common.WaveformCodec
import dev.scrybe.core.database.CustomRecordingTypeDao
import dev.scrybe.core.database.CustomRecordingTypeEntity
import dev.scrybe.core.database.FolderDao
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.SessionTaskDao
import dev.scrybe.core.database.SpeakerSegmentDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.CustomRecordingType
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.TransformProfile
import dev.scrybe.core.transforms.SessionTransformCoordinator
import dev.scrybe.service.recording.RecordingForegroundService
import dev.scrybe.service.recording.RecordingServiceActions
import dev.scrybe.service.recording.RecordingSessionEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val audioRecorder: AudioRecorder,
        private val recordingSessionDao: RecordingSessionDao,
        private val transcriptDao: TranscriptDao,
        private val speakerSegmentDao: SpeakerSegmentDao,
        private val sessionTaskDao: SessionTaskDao,
        private val folderDao: FolderDao,
        private val transformProfileDao: TransformProfileDao,
        private val customRecordingTypeDao: CustomRecordingTypeDao,
        private val sessionTransformCoordinator: SessionTransformCoordinator,
        private val preferencesDataStore: AppPreferencesDataStore,
        private val recordingSessionEvents: RecordingSessionEvents,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CaptureUiState())
        val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

        private val _transformDialog = MutableStateFlow<CaptureTransformDialogState?>(null)
        val transformDialog: StateFlow<CaptureTransformDialogState?> = _transformDialog.asStateFlow()

        val profiles: StateFlow<List<TransformProfile>> =
            transformProfileDao
                .getAllProfiles()
                .map { entities ->
                    entities.map { e ->
                        TransformProfile(
                            id = e.id,
                            name = e.name,
                            description = e.description,
                            systemPrompt = e.systemPrompt,
                            steps = TransformStepsCodec.decode(e.steps, fallback = e.systemPrompt),
                            providerType = ProviderType.valueOf(e.providerType),
                            isDefault = e.isDefault,
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val customTypes: StateFlow<List<CustomRecordingType>> =
            customRecordingTypeDao
                .getAll()
                .map { entities ->
                    entities.map { e ->
                        CustomRecordingType(
                            id = e.id,
                            name = e.name,
                            defaultProfileId = e.defaultProfileId,
                            createdAt = e.createdAt,
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        init {
            viewModelScope.launch {
                audioRecorder.telemetry.collectLatest { telemetry ->
                    val currentState = _uiState.value
                    val nextPhase =
                        when {
                            telemetry.elapsedMs > 0L || telemetry.amplitudeRatio > 0f -> CapturePhase.RECORDING
                            currentState.phase == CapturePhase.STOPPING -> CapturePhase.IDLE
                            else -> currentState.phase
                        }
                    val nextHistory =
                        when (nextPhase) {
                            CapturePhase.RECORDING ->
                                (currentState.amplitudeHistory + telemetry.amplitudeRatio)
                                    .takeLast(MAX_HISTORY)
                            else -> emptyList()
                        }
                    _uiState.value =
                        currentState.copy(
                            phase = nextPhase,
                            elapsedMs = telemetry.elapsedMs,
                            currentAmplitudeRatio = telemetry.amplitudeRatio,
                            amplitudeHistory = nextHistory,
                        )
                }
            }
            viewModelScope.launch {
                audioRecorder.isRecording.collectLatest { isRecording ->
                    val phase = _uiState.value.phase
                    if (!isRecording && (phase == CapturePhase.RECORDING || phase == CapturePhase.PAUSED)) {
                        _uiState.value =
                            _uiState.value.copy(
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
                recordingSessionEvents.recordingErrors.collectLatest { message ->
                    _uiState.value =
                        _uiState.value.copy(
                            phase = CapturePhase.IDLE,
                            elapsedMs = 0L,
                            currentAmplitudeRatio = 0f,
                            amplitudeHistory = emptyList(),
                            errorMessage = message,
                        )
                }
            }
            viewModelScope.launch {
                combine(
                    recordingSessionDao.getAllSessions(),
                    transcriptDao.getAllTranscripts(),
                    sessionTaskDao.getOpenTaskCountsPerSession(),
                    folderDao.getAllFolders(),
                ) { sessions, transcripts, taskCounts, folders ->
                    val transcriptLookup =
                        transcripts
                            .groupBy { it.sessionId }
                            .mapValues { (_, values) ->
                                values
                                    .sortedByDescending { it.createdAt }
                                    .firstOrNull { it.type == "EDITED" }
                                    ?.content
                                    ?: values.maxByOrNull { it.createdAt }?.content
                            }
                    val speakerCounts =
                        sessions.associate { session ->
                            session.id to
                                speakerSegmentDao
                                    .getSegmentsOnce(session.id)
                                    .map { it.speakerId }
                                    .distinct()
                                    .size
                        }
                    val taskCountMap = taskCounts.associate { it.sessionId to it.count }
                    val folderNameMap = folders.associate { it.id to it.name }
                    val mapped =
                        sessions.map { session ->
                            RecentCaptureSession(
                                id = session.id,
                                title = session.title,
                                createdAtLabel =
                                    java.time.Instant
                                        .ofEpochMilli(session.createdAt)
                                        .atZone(ZoneId.systemDefault())
                                        .format(RECENT_TIME_FORMATTER),
                                durationMs = session.durationMs,
                                status =
                                    dev.scrybe.core.model.SessionStatus
                                        .valueOf(session.status),
                                mode = runCatching { RecordingMode.valueOf(session.mode) }.getOrDefault(RecordingMode.JOURNAL),
                                tags = TagsCodec.decode(session.tags),
                                locationLabel = session.locationLabel,
                                transcriptPreview = transcriptLookup[session.id],
                                isArchived = session.isArchived,
                                folderId = session.folderId,
                                speakerCount = speakerCounts[session.id] ?: 0,
                                openTaskCount = taskCountMap[session.id] ?: 0,
                                waveformSamples = WaveformCodec.decode(session.waveformSamples).take(40),
                            )
                        }
                    mapped to folderNameMap
                }.collectLatest { (recentSessions, folderNameMap) ->
                    val openTotal = recentSessions.sumOf { it.openTaskCount }
                    _uiState.value =
                        _uiState.value.copy(
                            recentSessions = recentSessions,
                            openTaskTotal = openTotal,
                            folderNames = folderNameMap,
                        )
                }
            }
            viewModelScope.launch {
                customTypes.collectLatest { types ->
                    _uiState.value = _uiState.value.copy(customTypes = types)
                }
            }
        }

        fun showModePicker() {
            _uiState.value = _uiState.value.copy(showModePickerSheet = true)
        }

        fun dismissModePicker() {
            _uiState.value = _uiState.value.copy(showModePickerSheet = false)
        }

        fun startRecordingWithMode(mode: RecordingMode) {
            viewModelScope.launch {
                _uiState.value =
                    CaptureUiState(
                        phase = CapturePhase.RECORDING,
                        keepScreenOn = _uiState.value.keepScreenOn,
                        showModePickerSheet = false,
                        activeMode = mode,
                    )
                val intent =
                    Intent(context, RecordingForegroundService::class.java).apply {
                        action = RecordingServiceActions.ACTION_START
                        putExtra(RecordingServiceActions.EXTRA_RECORDING_MODE, mode.name)
                    }
                context.startForegroundService(intent)
            }
        }

        fun startRecordingWithCustomType(typeId: String) {
            viewModelScope.launch {
                _uiState.value =
                    CaptureUiState(
                        phase = CapturePhase.RECORDING,
                        keepScreenOn = _uiState.value.keepScreenOn,
                        showModePickerSheet = false,
                    )
                val intent =
                    Intent(context, RecordingForegroundService::class.java).apply {
                        action = RecordingServiceActions.ACTION_START
                        putExtra(RecordingServiceActions.EXTRA_RECORDING_MODE, RecordingMode.JOURNAL.name)
                        putExtra(RecordingServiceActions.EXTRA_CUSTOM_TYPE_ID, typeId)
                    }
                context.startForegroundService(intent)
            }
        }

        fun createCustomType(
            name: String,
            defaultProfileId: String?,
        ) {
            viewModelScope.launch {
                customRecordingTypeDao.insert(
                    CustomRecordingTypeEntity(
                        id =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                        name = name,
                        defaultProfileId = defaultProfileId,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

        fun deleteCustomType(id: String) {
            viewModelScope.launch { customRecordingTypeDao.delete(id) }
        }

        fun pauseRecording() {
            _uiState.value = _uiState.value.copy(phase = CapturePhase.PAUSED)
            val intent =
                Intent(context, RecordingForegroundService::class.java).apply {
                    action = RecordingServiceActions.ACTION_PAUSE
                }
            context.startService(intent)
        }

        fun resumeRecording() {
            _uiState.value = _uiState.value.copy(phase = CapturePhase.RECORDING)
            val intent =
                Intent(context, RecordingForegroundService::class.java).apply {
                    action = RecordingServiceActions.ACTION_RESUME
                }
            context.startService(intent)
        }

        fun stopRecording() {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(phase = CapturePhase.STOPPING, errorMessage = null)
                val intent =
                    Intent(context, RecordingForegroundService::class.java).apply {
                        action = RecordingServiceActions.ACTION_STOP
                    }
                context.startService(intent)
            }
        }

        fun cancelRecording() {
            viewModelScope.launch {
                val intent =
                    Intent(context, RecordingForegroundService::class.java).apply {
                        action = RecordingServiceActions.ACTION_CANCEL
                    }
                context.startService(intent)
                _uiState.value = CaptureUiState(keepScreenOn = _uiState.value.keepScreenOn)
            }
        }

        fun enterSelectionMode(sessionId: String) {
            _uiState.value = _uiState.value.copy(selectedSessionIds = setOf(sessionId))
        }

        fun toggleSelection(sessionId: String) {
            val current = _uiState.value.selectedSessionIds
            _uiState.value =
                _uiState.value.copy(
                    selectedSessionIds =
                        if (sessionId in current) current - sessionId else current + sessionId,
                )
        }

        fun clearSelection() {
            _uiState.value = _uiState.value.copy(selectedSessionIds = emptySet())
        }

        fun openTransformDialog() {
            val ids = _uiState.value.selectedSessionIds.toList()
            if (ids.isEmpty()) return
            val titles =
                ids.mapNotNull { id ->
                    _uiState.value.recentSessions
                        .find { it.id == id }
                        ?.title
                }
            _transformDialog.value = CaptureTransformDialogState(sessionIds = ids, sessionTitles = titles)
        }

        fun runTransformFromDialog(profile: TransformProfile) {
            val dialog = _transformDialog.value ?: return
            _transformDialog.value = dialog.copy(runningProfileId = profile.id, result = null)
            viewModelScope.launch {
                val ids = dialog.sessionIds
                val outcome =
                    if (ids.size == 1) {
                        sessionTransformCoordinator
                            .transformLatestRawTranscript(ids.first(), profile.id)
                            .map { it.content }
                    } else {
                        sessionTransformCoordinator
                            .transformCombinedLatestTranscripts(ids, profile.id)
                            .map { it.transcript.content }
                    }
                _transformDialog.value =
                    _transformDialog.value?.copy(
                        runningProfileId = null,
                        result = outcome.getOrNull()?.let { CaptureTransformResult(profile.name, it) },
                    )
            }
        }

        fun closeTransformDialog() {
            _transformDialog.value = null
        }

        fun deleteSelectedSessions() {
            viewModelScope.launch {
                val ids = _uiState.value.selectedSessionIds.toList()
                for (id in ids) {
                    val session = recordingSessionDao.getSessionByIdOnce(id) ?: continue
                    runCatching { File(session.audioFilePath).delete() }
                    transcriptDao.deleteTranscriptsForSession(id)
                    speakerSegmentDao.deleteForSession(id)
                    recordingSessionDao.deleteSession(id)
                }
                clearSelection()
            }
        }

        fun setArchivedForSelected(archived: Boolean) {
            val now = System.currentTimeMillis()
            viewModelScope.launch {
                val ids = _uiState.value.selectedSessionIds.toList()
                for (id in ids) {
                    val session = recordingSessionDao.getSessionByIdOnce(id) ?: continue
                    recordingSessionDao.updateSession(
                        session.copy(
                            isArchived = archived,
                            status = if (archived) SessionStatus.ARCHIVED.name else session.status,
                            updatedAt = now,
                        ),
                    )
                }
                clearSelection()
            }
        }

        fun renameSession(
            sessionId: String,
            newTitle: String,
        ) {
            viewModelScope.launch {
                val session = recordingSessionDao.getSessionByIdOnce(sessionId) ?: return@launch
                recordingSessionDao.updateSession(
                    session.copy(title = newTitle.trim(), updatedAt = System.currentTimeMillis()),
                )
            }
        }

        private companion object {
            const val MAX_HISTORY = 120
            val RECENT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
        }
    }
