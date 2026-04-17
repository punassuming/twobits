package dev.scrybe.feature.sessiondetail

sealed interface SessionDetailEvent {
    data class Message(val text: String) : SessionDetailEvent

    data class ShareText(val title: String, val text: String) : SessionDetailEvent

    data class ShareFile(val title: String, val path: String, val mimeType: String) : SessionDetailEvent

    /**
     * Emitted after a transform completes successfully. Carries the transformed
     * text so the UI can display a review dialog with copy/share actions.
     */
    data class TransformResult(
        val profileName: String,
        val text: String,
    ) : SessionDetailEvent

    data class SendToExternal(
        val title: String,
        val text: String,
        val packageName: String,
        val action: String,
    ) : SessionDetailEvent
}
