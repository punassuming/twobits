package dev.scrybe.feature.tasks

import dev.scrybe.core.model.RecordingMode
import java.time.Instant

data class InboxTask(
    val id: String,
    val sessionId: String,
    val text: String,
    val assignee: String?,
    val dueLabel: String?,
    val isDone: Boolean,
    val sessionTitle: String,
    val sessionMode: RecordingMode,
    val sessionCreatedAt: Instant,
    val createdAt: Instant,
)

enum class TaskFilter { OPEN, TODAY, WEEK, MINE, DELEGATED, DONE }

data class TaskInboxCounts(
    val today: Int = 0,
    val week: Int = 0,
    val open: Int = 0,
    val mine: Int = 0,
    val delegated: Int = 0,
    val done: Int = 0,
)

sealed interface TaskInboxUiState {
    data object Loading : TaskInboxUiState

    data class Success(
        val tasks: List<InboxTask>,
        val filter: TaskFilter,
        val counts: TaskInboxCounts,
    ) : TaskInboxUiState
}
