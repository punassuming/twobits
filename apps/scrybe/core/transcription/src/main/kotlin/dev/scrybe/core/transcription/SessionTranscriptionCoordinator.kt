package dev.scrybe.core.transcription

import android.util.Log
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.RecordingSessionEntity
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
        private val batchTranscriptionService: BatchTranscriptionService,
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
            val aiFeaturesProviderType =
                runCatching {
                    ProviderType.valueOf(preferencesDataStore.aiFeaturesProvider.first())
                }.getOrDefault(ProviderType.OPENAI)

            val audioFile = File(session.audioFilePath)
            if (!audioFile.exists()) {
                updateSessionStatus(sessionId, SessionStatus.FAILED)
                return Result.failure(IllegalStateException("Audio file not found for session $sessionId"))
            }

            updateSessionStatus(sessionId, SessionStatus.TRANSCRIBING)

            val transcriptionOptions =
                if (providerType == ProviderType.OPENAI) {
                    val model = preferencesDataStore.cloudTranscriptionModel.first()
                    TranscriptionOptions(model = model.apiName)
                } else {
                    TranscriptionOptions()
                }

            val result =
                batchTranscriptionService.transcribe(
                    sessionId = sessionId,
                    audioFile = audioFile,
                    providerType = providerType,
                    options = transcriptionOptions,
                )

            return result.fold(
                onSuccess = { batchResult ->
                    val transcriptEntity = insertRawTranscript(sessionId, batchResult.text, providerType)

                    if (batchResult.isPartial) {
                        updateSessionStatus(sessionId, SessionStatus.PARTIAL_TRANSCRIPTION)
                        Log.w(
                            TAG,
                            "Session $sessionId partially transcribed: " +
                                "${batchResult.completedChunks}/${batchResult.totalChunks} chunks",
                        )
                        return Result.success(transcriptEntity)
                    }

                    markTranscribedAndLaunchFollowUps(sessionId, session, batchResult.text, aiFeaturesProviderType)
                    Result.success(transcriptEntity)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to transcribe session $sessionId", error)
                    updateSessionStatus(sessionId, SessionStatus.FAILED)
                    Result.failure(error)
                },
            )
        }

        suspend fun retranscribePartial(sessionId: String): Result<TranscriptEntity> = transcribeSession(sessionId)

        /**
         * Accepts a transcript produced by a live realtime session as the final result for
         * [sessionId], skipping the batch API call entirely — used when
         * [RecordingForegroundService][dev.scrybe.service.recording.RecordingForegroundService]'s
         * realtime stream already produced usable text, so the batch call that would otherwise
         * run in [autoTranscribeIfEnabled] is skipped (avoids double-billing Pro users for the
         * same recording). Shares the persist/status/follow-up path [transcribeSession] uses for
         * a full (non-partial) success.
         */
        suspend fun acceptRealtimeTranscript(
            sessionId: String,
            text: String,
        ): Result<TranscriptEntity> =
            runCatching {
                val session =
                    sessionDao.getSessionByIdOnce(sessionId)
                        ?: error("Session $sessionId not found")
                val aiFeaturesProviderType =
                    runCatching {
                        ProviderType.valueOf(preferencesDataStore.aiFeaturesProvider.first())
                    }.getOrDefault(ProviderType.OPENAI)

                val transcriptEntity = insertRawTranscript(sessionId, text, ProviderType.OPENAI)
                markTranscribedAndLaunchFollowUps(sessionId, session, text, aiFeaturesProviderType)
                transcriptEntity
            }

        private suspend fun insertRawTranscript(
            sessionId: String,
            text: String,
            providerType: ProviderType,
        ): TranscriptEntity {
            transcriptDao.deleteTranscriptsForSessionAndType(sessionId = sessionId, type = TranscriptType.RAW.name)
            val transcriptEntity =
                TranscriptEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    content = text,
                    type = TranscriptType.RAW.name,
                    sourceTranscriptId = null,
                    providerType = providerType.name,
                    transformProfileId = null,
                    transformRunId = null,
                    createdAt = System.currentTimeMillis(),
                )
            transcriptDao.insertTranscript(transcriptEntity)
            return transcriptEntity
        }

        private suspend fun markTranscribedAndLaunchFollowUps(
            sessionId: String,
            session: RecordingSessionEntity,
            text: String,
            aiFeaturesProviderType: ProviderType,
        ) {
            updateSessionStatus(
                sessionId = sessionId,
                status = SessionStatus.TRANSCRIBED,
                // Realtime billing granularity isn't confirmed yet (see Phase W0 notes) — this
                // reuses the batch-per-minute estimate as a placeholder display figure; it does
                // not drive actual billing, which is enforced server-side by the Worker.
                estimatedCostUsd = TranscriptionPricing.estimateUsd(session.durationMs),
            )
            if (preferencesDataStore.enableSpeakerIdentification.first()) {
                launchDiarization(sessionId, File(session.audioFilePath), text, aiFeaturesProviderType)
            }
            if (preferencesDataStore.enableInsightAnalysis.first()) {
                launchInsights(sessionId, text, session.durationMs, aiFeaturesProviderType)
            }
        }

        suspend fun fetchSpeakerInfo(sessionId: String): Result<Int> =
            runCatching {
                val session =
                    sessionDao.getSessionByIdOnce(sessionId)
                        ?: error("Session $sessionId not found")
                val transcriptText =
                    transcriptDao
                        .getLatestTranscriptByType(sessionId, TranscriptType.EDITED.name)
                        ?.content
                        ?.takeIf { it.isNotBlank() }
                        ?: transcriptDao
                            .getLatestTranscriptByType(sessionId, TranscriptType.RAW.name)
                            ?.content
                            ?.takeIf { it.isNotBlank() }
                        ?: error("Transcribe this recording before retrieving speakers")

                val audioFile = File(session.audioFilePath)
                require(audioFile.exists()) { "Audio file not found for session $sessionId" }

                val providerType =
                    runCatching {
                        ProviderType.valueOf(preferencesDataStore.aiFeaturesProvider.first())
                    }.getOrDefault(ProviderType.OPENAI)

                val segments =
                    diarizationService
                        .diarize(sessionId, audioFile, transcriptText, providerType)
                        .getOrThrow()

                // Preserve manual person assignments before wiping speaker rows.
                val existingPersonIds =
                    speakerSegmentDao
                        .getSegmentsOnce(sessionId)
                        .groupBy { it.speakerId }
                        .mapValues { (_, segs) -> segs.firstOrNull { it.personId != null }?.personId }

                speakerSegmentDao.deleteForSession(sessionId)
                speakerSegmentDao.insertSegments(
                    segments.map { segment ->
                        dev.scrybe.core.database.SpeakerSegmentEntity(
                            id = segment.id,
                            sessionId = segment.sessionId,
                            speakerId = segment.speakerId,
                            speakerLabel = segment.speakerLabel,
                            personId = segment.personId,
                            startMs = segment.startMs,
                            endMs = segment.endMs,
                        )
                    },
                )

                // Restore personId links that the user assigned manually.
                for ((speakerId, personId) in existingPersonIds) {
                    if (personId != null) {
                        speakerSegmentDao.updatePersonId(sessionId, speakerId, personId)
                    }
                }

                segments
                    .map { it.speakerId }
                    .distinct()
                    .size
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

                val existingPersonIds =
                    speakerSegmentDao
                        .getSegmentsOnce(sessionId)
                        .groupBy { it.speakerId }
                        .mapValues { (_, segs) -> segs.firstOrNull { it.personId != null }?.personId }

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

                for ((speakerId, personId) in existingPersonIds) {
                    if (personId != null) {
                        speakerSegmentDao.updatePersonId(sessionId, speakerId, personId)
                    }
                }
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
