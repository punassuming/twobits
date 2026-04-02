package dev.scrybe.core.transforms

import dev.scrybe.core.common.TransformStepsCodec
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.database.TranscriptEntity
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TransformProfileEntity
import dev.scrybe.core.database.TransformRunDao
import dev.scrybe.core.database.TransformRunEntity
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.TranscriptType
import dev.scrybe.core.model.TransformStatus
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CombinedTransformResult(
    val anchorSessionId: String,
    val anchorSessionTitle: String,
    val transcript: TranscriptEntity,
    val includedSessionCount: Int,
    val skippedSessionCount: Int,
)

@Singleton
class SessionTransformCoordinator
    @Inject
    constructor(
        private val recordingSessionDao: RecordingSessionDao,
        private val transcriptDao: TranscriptDao,
        private val transformProfileDao: TransformProfileDao,
        private val transformRunDao: TransformRunDao,
        private val transformationPipeline: TransformationPipeline,
    ) {
        suspend fun transformLatestRawTranscript(
            sessionId: String,
            profileId: String,
        ): Result<TranscriptEntity> {
            val source =
                loadSessionTranscriptSource(sessionId)
                    ?: return Result.failure(IllegalStateException("No transcript available"))
            val profile = loadProfile(profileId)
            return runProfileTransform(
                anchorSessionId = source.sessionId,
                inputTranscriptId = source.inputTranscript.id,
                transcriptText = source.rawTranscript.content,
                currentText = source.inputTranscript.content,
                combinedTranscriptText = null,
                sourceTranscriptId = source.inputTranscript.id,
                profile = profile,
            )
        }

        suspend fun transformCombinedLatestTranscripts(
            sessionIds: List<String>,
            profileId: String,
        ): Result<CombinedTransformResult> {
            val distinctSessionIds = sessionIds.distinct()
            val sources =
                distinctSessionIds
                    .mapNotNull { sessionId -> loadSessionTranscriptSource(sessionId) }
                    .sortedByDescending { it.createdAt }
            if (sources.isEmpty()) {
                return Result.failure(IllegalStateException("Transcribe at least one selected recording before consolidating"))
            }

            val profile = loadProfile(profileId)
            val combinedTranscriptText = buildCombinedTranscriptText(sources)
            val anchor = sources.first()
            return runProfileTransform(
                anchorSessionId = anchor.sessionId,
                inputTranscriptId = anchor.inputTranscript.id,
                transcriptText = combinedTranscriptText,
                currentText = combinedTranscriptText,
                combinedTranscriptText = combinedTranscriptText,
                sourceTranscriptId = anchor.inputTranscript.id,
                profile = profile,
            ).map { transcript ->
                CombinedTransformResult(
                    anchorSessionId = anchor.sessionId,
                    anchorSessionTitle = anchor.sessionTitle,
                    transcript = transcript,
                    includedSessionCount = sources.size,
                    skippedSessionCount = distinctSessionIds.size - sources.size,
                )
            }
        }

        private suspend fun loadSessionTranscriptSource(
            sessionId: String,
        ): SessionTranscriptSource? {
            val session = recordingSessionDao.getSessionByIdOnce(sessionId) ?: return null
            val transcripts = transcriptDao.getTranscriptsForSession(sessionId).first()
            val inputTranscript =
                transcripts
                    .sortedByDescending { it.createdAt }
                    .firstOrNull { it.type == TranscriptType.EDITED.name }
                    ?: transcripts.lastOrNull { it.type == TranscriptType.RAW.name }
                    ?: return null
            val rawTranscript =
                transcripts.lastOrNull { it.type == TranscriptType.RAW.name }
                    ?: return null
            return SessionTranscriptSource(
                sessionId = session.id,
                sessionTitle = session.title,
                createdAt = session.createdAt,
                inputTranscript = inputTranscript,
                rawTranscript = rawTranscript,
            )
        }

        private suspend fun loadProfile(profileId: String): TransformProfileEntity {
            return transformProfileDao.getProfileById(profileId)
                ?: throw IllegalStateException("Profile not found")
        }

        private suspend fun runProfileTransform(
            anchorSessionId: String,
            inputTranscriptId: String,
            transcriptText: String,
            currentText: String,
            combinedTranscriptText: String?,
            sourceTranscriptId: String,
            profile: TransformProfileEntity,
        ): Result<TranscriptEntity> {
            val steps = TransformStepsCodec.decode(profile.steps, fallback = profile.systemPrompt)
            if (steps.isEmpty()) {
                return Result.failure(IllegalStateException("Profile has no transform steps"))
            }

            val runId = UUID.randomUUID().toString()
            val startedAt = System.currentTimeMillis()
            transformRunDao.insertRun(
                TransformRunEntity(
                    id = runId,
                    sessionId = anchorSessionId,
                    profileId = profile.id,
                    inputTranscriptId = inputTranscriptId,
                    outputTranscriptId = null,
                    status = TransformStatus.RUNNING.name,
                    errorMessage = null,
                    startedAt = startedAt,
                    completedAt = null,
                ),
            )

            val providerType = ProviderType.valueOf(profile.providerType)
            val transformResult =
                runCatching {
                    var intermediateText = currentText
                    steps.forEach { stepPrompt ->
                        val stepResult =
                            transformationPipeline.execute(
                                input =
                                    TransformInput(
                                        sessionId = anchorSessionId,
                                        transcriptId = inputTranscriptId,
                                        transcriptText = transcriptText,
                                        currentText = intermediateText,
                                        profileId = profile.id,
                                        systemPrompt = stepPrompt,
                                        combinedTranscriptText = combinedTranscriptText,
                                    ),
                                providerType = providerType,
                            ).getOrThrow()
                        intermediateText = stepResult.transformedText
                    }
                    intermediateText
                }

            return transformResult.fold(
                onSuccess = { transformedText ->
                    persistSuccessfulTransform(
                        anchorSessionId = anchorSessionId,
                        inputTranscriptId = inputTranscriptId,
                        sourceTranscriptId = sourceTranscriptId,
                        profile = profile,
                        runId = runId,
                        startedAt = startedAt,
                        transformedText = transformedText,
                    )
                },
                onFailure = { error ->
                    persistFailedTransform(
                        anchorSessionId = anchorSessionId,
                        inputTranscriptId = inputTranscriptId,
                        profile = profile,
                        runId = runId,
                        startedAt = startedAt,
                        error = error,
                    )
                },
            )
        }

        private suspend fun persistSuccessfulTransform(
            anchorSessionId: String,
            inputTranscriptId: String,
            sourceTranscriptId: String,
            profile: TransformProfileEntity,
            runId: String,
            startedAt: Long,
            transformedText: String,
        ): Result<TranscriptEntity> {
            val transcript =
                TranscriptEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = anchorSessionId,
                    content = transformedText,
                    type = TranscriptType.TRANSFORMED.name,
                    sourceTranscriptId = sourceTranscriptId,
                    providerType = profile.providerType,
                    transformProfileId = profile.id,
                    transformRunId = runId,
                    createdAt = System.currentTimeMillis(),
                )
            transcriptDao.insertTranscript(transcript)
            transformRunDao.insertRun(
                TransformRunEntity(
                    id = runId,
                    sessionId = anchorSessionId,
                    profileId = profile.id,
                    inputTranscriptId = inputTranscriptId,
                    outputTranscriptId = transcript.id,
                    status = TransformStatus.SUCCESS.name,
                    errorMessage = null,
                    startedAt = startedAt,
                    completedAt = System.currentTimeMillis(),
                ),
            )
            return Result.success(transcript)
        }

        private suspend fun persistFailedTransform(
            anchorSessionId: String,
            inputTranscriptId: String,
            profile: TransformProfileEntity,
            runId: String,
            startedAt: Long,
            error: Throwable,
        ): Result<TranscriptEntity> {
            transformRunDao.insertRun(
                TransformRunEntity(
                    id = runId,
                    sessionId = anchorSessionId,
                    profileId = profile.id,
                    inputTranscriptId = inputTranscriptId,
                    outputTranscriptId = null,
                    status = TransformStatus.FAILED.name,
                    errorMessage = error.message,
                    startedAt = startedAt,
                    completedAt = System.currentTimeMillis(),
                ),
            )
            return Result.failure(error)
        }

        private fun buildCombinedTranscriptText(
            sources: List<SessionTranscriptSource>,
        ): String =
            sources.joinToString("\n\n---\n\n") { source ->
                buildString {
                    appendLine(source.sessionTitle)
                    appendLine("Recorded ${formatCombinedTranscriptTime(source.createdAt)}")
                    appendLine()
                    append(source.inputTranscript.content.trim())
                }
            }

        private fun formatCombinedTranscriptTime(createdAt: Long): String =
            Instant.ofEpochMilli(createdAt)
                .atZone(ZoneId.systemDefault())
                .format(COMBINED_TIME_FORMATTER)

        private data class SessionTranscriptSource(
            val sessionId: String,
            val sessionTitle: String,
            val createdAt: Long,
            val inputTranscript: TranscriptEntity,
            val rawTranscript: TranscriptEntity,
        )

        private companion object {
            val COMBINED_TIME_FORMATTER: DateTimeFormatter =
                DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
        }
    }
