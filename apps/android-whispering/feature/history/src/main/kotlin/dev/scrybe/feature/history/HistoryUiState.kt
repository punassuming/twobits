package dev.scrybe.feature.history

import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.SessionStatus
import java.time.Instant

data class HistorySessionItem(
    val session: RecordingSession,
    val transcriptPreview: String? = null,
)

enum class RecordsSortOption {
    NEWEST,
    OLDEST,
    LONGEST,
    LARGEST,
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
)

data class RecordInfo(
    val title: String,
    val createdAt: Instant,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val audioFormat: String,
    val sampleRateHz: Int,
    val encodingBitRate: Int,
    val channelCount: Int,
    val filePath: String,
    val transcriptPreview: String?,
)

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(
        val sessions: List<HistorySessionItem>,
        val filters: RecordsFilterState,
    ) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}
