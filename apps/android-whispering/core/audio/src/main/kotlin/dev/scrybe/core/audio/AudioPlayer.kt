package dev.scrybe.core.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {
    val playbackState: StateFlow<PlaybackState>
    suspend fun play(filePath: String): Result<Unit>
    fun pause()
    fun seekTo(positionMs: Long)
    fun stop()
}
