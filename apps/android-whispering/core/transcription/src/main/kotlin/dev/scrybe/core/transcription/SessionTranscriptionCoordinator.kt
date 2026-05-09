package dev.scrybe.core.transcription

import android.util.Log
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.SpeakerSegmentDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.database.TranscriptEntity
import dev.scrybe.core.datastore.AppPreferencesDataStore
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.SessionStatus
import dev.scrybe.core.model.TranscriptType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionTranscriptionCoordinator
    @Inject
    constructor(
        private val sessionDao: RecordingSessionDao,
        private val transcriptDao: TranscriptDao,
        private val speakerSegmentDao: SpeakerSegmentDao,
        private val preferencesDataStore: AppPreferencesDataStore,
        private val transcriptionOrchestrator: TranscriptionOrchestrator,
        private val diarizationService: DiarizationService,
        private val insightService: InsightService,
    ) {
        private val postTranscriptionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        suspend fun autoTranscribeIfEnabled(sessionId: String): Result<Boolean> {
            if (!preferencesDataStore.autoTranscribe.first()) {
                return Result.success(false)
            }

            return transcribeSession(sessionId).map { true }
        }

        suspend fun transcribeSession(sessionId: String): Result<TranscriptEntity> {
            val session =
                sessionDao.getSessionByIdOnce(sessionId)
                    ?: return Result.failure(IllegalArgumentException("Session $sessionId not found"))

            val providerType =
                runCatching {
                    ProviderType.valueOf(preferencesDataStore.defaultProvider.first())
                }.getOrDefault(ProviderType.OPENAI)

            val audioFile = File(session.audioFilePath)
            if (!audioFile.exists()) {
                updateSessionStatus(sessionId, SessionStatus.FAILED)
                return Result.failure(IllegalStateException("Audio file not found for session $sessionId"))
            }

            updateSessionStatus(sessionId, SessionStatus.TRANSCRIBING)

            val result =
                transcriptionOrchestrator.transcribe(
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
                    val transcriptEntity =
                        TranscriptEntity(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            content = transcript.text,
                            type = TranscriptType.RAW.name,
                            sourceTranscriptId = null,
                            providerType = providerType.name,
                            transformProfileId = null,
                            transformRunId = null,
                            createdAt = System.currentTimeMillis(),
                        )
                    transcriptDao.insertTranscript(transcriptEntity)
                    updateSessionStatus(
                        sessionId = sessionId,
                        status = SessionStatus.TRANSCRIBED,
                        estimatedCostUsd = TranscriptionPricing.estimateUsd(session.durationMs),
                    )
                    if (preferencesDataStore.enableSpeakerIdentification.first()) {
                        launchDiarization(sessionId, audioFile, transcript.text, providerType)
                    }
                    if (preferencesDataStore.enableInsightAnalysis.first()) {
                        launchInsights(sessionId, transcript.text, session.durationMs, providerType)
                    }
                    Result.success(transcriptEntity)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to transcribe session $sessionId", error)
                    updateSessionStatus(sessionId, SessionStatus.FAILED)
                    Result.failure(error)
                },
            )
        }

        private fun launchDiarization(
            sessionId: String,
            audioFile: File,
            transcriptText: String,
            providerType: ProviderType,
        ) {
            postTranscriptionScope.launch {
                val segments =
                    diarizationService
                        .diarize(sessionId, audioFile, transcriptText, providerType)
                        .getOrNull()
                        ?: return@launch
                speakerSegmentDao.deleteForSession(sessionId)
                speakerSegmentDao.insertSegments(
                    segments.map { s ->
                        dev.scrybe.core.database.SpeakerSegmentEntity(
                            id = s.id,
                            sessionId = s.sessionId,
                            speakerId = s.speakerId,
                            speakerLabel = s.speakerLabel,
                            personId = s.personId,
                            startMs = s.startMs,
                            endMs = s.endMs,
                        )
                    },
                )
            }
        }

        private fun launchInsights(
            sessionId: String,
            transcriptText: String,
            durationMs: Long,
            providerType: ProviderType,
        ) {
            postTranscriptionScope.launch {
                val sentimentJson =
                    insightService
                        .analyzeSentiment(transcriptText, durationMs, providerType)
                        .getOrNull() ?: return@launch
                val topicsJson =
                    insightService
                        .extractTopics(transcriptText, durationMs, providerType)
                        .getOrNull() ?: return@launch
                sessionDao.updateInsights(
                    id = sessionId,
                    sentimentJson = sentimentJson,
                    topicsJson = topicsJson,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }

        private suspend fun updateSessionStatus(
            sessionId: String,
            status: SessionStatus,
            estimatedCostUsd: Double? = null,
        ) {
            val session = sessionDao.getSessionByIdOnce(sessionId) ?: return
            sessionDao.updateSession(
                session.copy(
                    status = status.name,
                    estimatedTranscriptionCostUsd = estimatedCostUsd ?: session.estimatedTranscriptionCostUsd,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }

        private companion object {
            const val TAG = "SessionTranscription"
        }
    }
