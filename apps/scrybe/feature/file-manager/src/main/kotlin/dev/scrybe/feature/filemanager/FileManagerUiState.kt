package dev.scrybe.feature.filemanager

import java.io.File

data class PendingImport(
    val file: File,
    val defaultTimestampMs: Long,
    val deleteOnCancel: Boolean,
)

data class RecordingFileEntry(
    val absolutePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val sessionId: String?,
    val hasTranscript: Boolean,
)

data class OutputFileEntry(
    val absolutePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val category: String,
)

data class ModelFileEntry(
    val absolutePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val isOrphaned: Boolean,
)

sealed interface FileManagerUiState {
    object Loading : FileManagerUiState

    data class Success(
        val recordings: List<RecordingFileEntry>,
        val outputs: List<OutputFileEntry>,
        val models: List<ModelFileEntry>,
    ) : FileManagerUiState

    data class Error(
        val message: String,
    ) : FileManagerUiState
}
