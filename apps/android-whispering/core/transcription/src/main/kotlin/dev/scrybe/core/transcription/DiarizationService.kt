package dev.scrybe.core.transcription

import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.SpeakerSegment
import java.io.File

interface DiarizationService {
    suspend fun diarize(
        sessionId: String,
        audioFile: File,
        transcriptText: String,
        providerType: ProviderType,
    ): Result<List<SpeakerSegment>>
}
