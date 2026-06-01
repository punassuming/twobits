package dev.scrybe.core.audio

import kotlinx.coroutines.flow.Flow

interface AudioRecorder {
    val isRecording: Flow<Boolean>
    val telemetry: Flow<RecordingTelemetry>

    suspend fun startRecording(config: RecordingConfig): Result<Unit>

    suspend fun stopRecording(): Result<RecordedAudio>

    suspend fun pauseRecording()

    suspend fun resumeRecording()

    fun cancelRecording()
}
