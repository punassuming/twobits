package dev.scrybe.core.audio

data class PlaybackState(
    val filePath: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
)
