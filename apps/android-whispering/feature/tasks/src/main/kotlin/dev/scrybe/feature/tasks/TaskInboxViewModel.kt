package dev.scrybe.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.scrybe.core.common.TagsCodec
import dev.scrybe.core.common.WaveformCodec
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.SessionTaskDao
import dev.scrybe.core.model.AudioFormat
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private val TODAY_LABELS = setOf("today", "Today")
private val WEEK_LABELS = setOf("today", "Today", "Mon", "Tue", "Wed", "Thu", "Fri")

@HiltViewModel
class TaskInboxViewModel
    @Inject
    constructor(
        private val sessionTaskDao: SessionTaskDao,
        private val recordingSessionDao: RecordingSessionDao,
    ) : ViewModel() {
        private val filter = MutableStateFlow(TaskFilter.OPEN)

        val uiState: StateFlow<TaskInboxUiState> =
            combine(
                sessionTaskDao.getAllTasks(),
                recordingSessionDao.getAllSessions(),
                filter,
            ) { taskEntities, sessionEntities, currentFilter ->
                val sessionMap =
                    sessionEntities.associate { entity ->
                        entity.id to
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
                                locationLabel = entity.locationLabel,
                                mode = RecordingMode.valueOf(entity.mode),
                                createdAt = Instant.ofEpochMilli(entity.createdAt),
                                updatedAt = Instant.ofEpochMilli(entity.updatedAt),
                            )
                    }
                val allTasks =
                    taskEntities.mapNotNull { entity ->
                        val session = sessionMap[entity.sessionId] ?: return@mapNotNull null
                        InboxTask(
                            id = entity.id,
                            sessionId = entity.sessionId,
                            text = entity.text,
                            assignee = entity.assignee,
                            dueLabel = entity.dueLabel,
                            isDone = entity.isDone,
                            sessionTitle = session.title,
                            sessionMode = session.mode,
                            sessionCreatedAt = session.createdAt,
                            createdAt = Instant.ofEpochMilli(entity.createdAt),
                        )
                    }
                val counts = buildCounts(allTasks)
                val filtered = applyFilter(allTasks, currentFilter)
                TaskInboxUiState.Success(tasks = filtered, filter = currentFilter, counts = counts)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskInboxUiState.Loading)

        fun setFilter(f: TaskFilter) {
            filter.value = f
        }

        fun toggleDone(
            id: String,
            isDone: Boolean,
        ) {
            viewModelScope.launch { sessionTaskDao.updateIsDone(id, !isDone) }
        }

        private fun buildCounts(tasks: List<InboxTask>): TaskInboxCounts =
            TaskInboxCounts(
                today = tasks.count { !it.isDone && it.dueLabel in TODAY_LABELS },
                week = tasks.count { !it.isDone && it.dueLabel in WEEK_LABELS },
                open = tasks.count { !it.isDone },
                mine = tasks.count { !it.isDone && it.assignee.isNullOrEmpty() },
                delegated = tasks.count { !it.isDone && !it.assignee.isNullOrEmpty() },
                done = tasks.count { it.isDone },
            )

        private fun applyFilter(
            tasks: List<InboxTask>,
            f: TaskFilter,
        ): List<InboxTask> =
            when (f) {
                TaskFilter.OPEN -> tasks.filter { !it.isDone }
                TaskFilter.TODAY -> tasks.filter { !it.isDone && it.dueLabel in TODAY_LABELS }
                TaskFilter.WEEK -> tasks.filter { !it.isDone && it.dueLabel in WEEK_LABELS }
                TaskFilter.MINE -> tasks.filter { !it.isDone && it.assignee.isNullOrEmpty() }
                TaskFilter.DELEGATED -> tasks.filter { !it.isDone && !it.assignee.isNullOrEmpty() }
                TaskFilter.DONE -> tasks.filter { it.isDone }
            }
    }
