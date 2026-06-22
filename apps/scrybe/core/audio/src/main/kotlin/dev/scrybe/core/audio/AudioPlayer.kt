package dev.scrybe.core.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {
    val playbackState: StateFlow<PlaybackState>

    suspend fun play(filePath: String): Result<Unit>

    fun prepare(
        filePath: String,
        startPositionMs: Long = 0L,
    ): Result<Unit>

    fun pause()

    fun seekTo(positionMs: Long)

    fun stop()

    fun setPlaybackSpeed(speed: Float)
}
