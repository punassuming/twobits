package dev.scrybe.core.transcription

import dev.scrybe.core.model.ProviderType
import java.io.File

interface TranscriptionProvider {
    val providerType: ProviderType
    suspend fun transcribe(audioFile: File, options: TranscriptionOptions): Result<TranscriptResult>
}
