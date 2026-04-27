package dev.scrybe.core.localai

sealed interface LocalModelState {
    data object NotDownloaded : LocalModelState

    data class Downloading(
        val progressPercent: Int,
    ) : LocalModelState

    data class Ready(
        val path: String,
    ) : LocalModelState

    data class Error(
        val message: String,
    ) : LocalModelState
}
