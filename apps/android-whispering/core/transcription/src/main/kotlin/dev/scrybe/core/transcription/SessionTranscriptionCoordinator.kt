package dev.scrybe.core.transcription

import android.util.Log
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.database.TranscriptEntity
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.TranscriptType
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class SessionTranscriptionCoordinator @Inject constructor(
    private val sessionDao: RecordingSessionDao,
    private val transcriptDao: TranscriptDao,
    private val preferencesDataStore: AppPreferencesDataStore,
    private val transcriptionOrchestrator: TranscriptionOrchestrator,
) {

    suspend fun autoTranscribeIfEnabled(sessionId: String): Result<Boolean> {
        if (!preferencesDataStore.autoTranscribe.first()) {
            return Result.success(false)
        }

        return transcribeSession(sessionId).map { true }
    }

    suspend fun transcribeSession(sessionId: String): Result<TranscriptEntity> {
        val session = sessionDao.getSessionByIdOnce(sessionId)
            ?: return Result.failure(IllegalArgumentException("Session $sessionId not found"))

        val providerType = runCatching {
            ProviderType.valueOf(preferencesDataStore.defaultProvider.first())
        }
            .getOrDefault(ProviderType.OPENAI)

        val audioFile = File(session.audioFilePath)
        if (!audioFile.exists()) {
            updateSessionStatus(sessionId, SessionStatus.FAILED)
            return Result.failure(IllegalStateException("Audio file not found for session $sessionId"))
        }

        updateSessionStatus(sessionId, SessionStatus.TRANSCRIBING)

        val result = transcriptionOrchestrator.transcribe(
            sessionId = sessionId,
            audioFile = audioFile,
            providerType = providerType,
        )

        return result.fold(
            onSuccess = { transcript ->
                transcriptDao.deleteTranscriptsForSessionAndType(
                    sessionId = sessionId,
                    type = TranscriptType.RAW.name,
                )
                val transcriptEntity = TranscriptEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    content = transcript.text,
                    type = TranscriptType.RAW.name,
                    providerType = providerType.name,
                    transformProfileId = null,
                    transformRunId = null,
                    createdAt = System.currentTimeMillis(),
                )
                transcriptDao.insertTranscript(transcriptEntity)
                updateSessionStatus(sessionId, SessionStatus.TRANSCRIBED)
                Result.success(transcriptEntity)
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to transcribe session $sessionId", error)
                updateSessionStatus(sessionId, SessionStatus.FAILED)
                Result.failure(error)
            },
        )
    }

    private suspend fun updateSessionStatus(sessionId: String, status: SessionStatus) {
        val session = sessionDao.getSessionByIdOnce(sessionId) ?: return
        sessionDao.updateSession(
            session.copy(
                status = status.name,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private companion object {
        const val TAG = "SessionTranscription"
    }
}
