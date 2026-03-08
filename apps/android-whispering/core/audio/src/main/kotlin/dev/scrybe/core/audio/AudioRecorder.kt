package dev.scrybe.core.audio

import kotlinx.coroutines.flow.Flow

interface AudioRecorder {
    val isRecording: Flow<Boolean>
    suspend fun startRecording(config: RecordingConfig): Result<Unit>
    suspend fun stopRecording(): Result<RecordedAudio>
    fun cancelRecording()
}
