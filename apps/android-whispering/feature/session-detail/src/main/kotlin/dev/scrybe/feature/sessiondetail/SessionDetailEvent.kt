package dev.scrybe.feature.sessiondetail

sealed interface SessionDetailEvent {
    data class Message(val text: String) : SessionDetailEvent
    data class ShareText(val title: String, val text: String) : SessionDetailEvent
}
