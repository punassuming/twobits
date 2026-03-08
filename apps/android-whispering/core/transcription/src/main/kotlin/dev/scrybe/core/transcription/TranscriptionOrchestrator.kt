package dev.scrybe.core.transcription

import dev.scrybe.core.model.ProviderType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionOrchestrator @Inject constructor(
    private val providers: Map<ProviderType, @JvmSuppressWildcards TranscriptionProvider>
) {
    private val mutex = Mutex()
    private val inProgress = mutableSetOf<String>()

    suspend fun transcribe(
        sessionId: String,
        audioFile: File,
        providerType: ProviderType,
        options: TranscriptionOptions = TranscriptionOptions(),
    ): Result<TranscriptResult> {
        mutex.withLock {
            if (sessionId in inProgress) {
                return Result.failure(IllegalStateException("Session $sessionId is already being transcribed"))
            }
            inProgress.add(sessionId)
        }
        return try {
            val provider = providers[providerType]
                ?: return Result.failure(IllegalArgumentException("No provider found for $providerType"))
            provider.transcribe(audioFile, options)
        } finally {
            mutex.withLock { inProgress.remove(sessionId) }
        }
    }
}
