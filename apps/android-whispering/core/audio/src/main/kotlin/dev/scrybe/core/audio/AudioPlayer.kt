package dev.scrybe.core.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioPlayer {
    val isPlaying: StateFlow<Boolean>
    suspend fun play(filePath: String): Result<Unit>
    fun stop()
}
