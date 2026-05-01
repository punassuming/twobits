package dev.scrybe.feature.filemanager

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

sealed interface FileManagerUiState {
    object Loading : FileManagerUiState

    data class Success(
        val recordings: List<RecordingFileEntry>,
        val outputs: List<OutputFileEntry>,
    ) : FileManagerUiState

    data class Error(
        val message: String,
    ) : FileManagerUiState
}
