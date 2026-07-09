package dev.scrybe.feature.history

import dev.scrybe.core.model.Folder
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import java.time.Instant

data class HistorySessionItem(
    val session: RecordingSession,
    val transcriptPreview: String? = null,
    val speakerCount: Int = 0,
    val openTaskCount: Int = 0,
    val customTypeName: String? = null,
    val customTypeIconName: String? = null,
)

data class FolderNode(
    val id: String,
    val name: String,
    val sessionCount: Int,
    val depth: Int,
)

enum class RecordsSortOption {
    NEWEST,
    OLDEST,
    LONGEST,
    LARGEST,
    ALPHABETICAL,
}

enum class RecordsDateRange {
    ALL,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
}

data class RecordsFilterState(
    val sortOption: RecordsSortOption = RecordsSortOption.NEWEST,
    val dateRange: RecordsDateRange = RecordsDateRange.ALL,
    val includedStatuses: Set<SessionStatus> = emptySet(),
    val showArchived: Boolean = false,
    val selectedTag: String? = null,
    val selectedMode: RecordingMode? = null,
)

data class RecordsInteractionPreferences(
    val confirmSwipeActions: Boolean = true,
    val showRecordingInfoInList: Boolean = true,
)

data class RecordInfo(
    val title: String,
    val tags: List<String>,
    val createdAt: Instant,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val audioFormat: String,
    val sampleRateHz: Int,
    val encodingBitRate: Int,
    val channelCount: Int,
    val transcriptPreview: String?,
)

data class RecordsSelectionState(
    val selectedSessionIds: Set<String> = emptySet(),
) {
    val isSelecting: Boolean = selectedSessionIds.isNotEmpty()
}

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data class Success(
        val sessions: List<HistorySessionItem>,
        val filters: RecordsFilterState,
        val interactionPreferences: RecordsInteractionPreferences = RecordsInteractionPreferences(),
        val selection: RecordsSelectionState = RecordsSelectionState(),
        val transformingSessionIds: Set<String> = emptySet(),
        val currentFolderId: String? = null,
        val subfolders: List<Folder> = emptyList(),
        val breadcrumb: List<Folder> = emptyList(),
        val allFolders: List<Folder> = emptyList(),
        val sessionsByFolderId: Map<String, List<HistorySessionItem>> = emptyMap(),
        val availableTags: List<Pair<String, Int>> = emptyList(),
        val semanticSearchLoading: Boolean = false,
        val semanticRankedIds: List<String>? = null,
        val openTaskCount: Int = 0,
        val sessionsWithTasksCount: Int = 0,
    ) : HistoryUiState

    data class Error(
        val message: String,
    ) : HistoryUiState
}
