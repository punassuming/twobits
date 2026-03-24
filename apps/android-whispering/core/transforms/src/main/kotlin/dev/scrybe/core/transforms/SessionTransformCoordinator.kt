package dev.scrybe.core.transforms

import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TransformRunDao
import dev.scrybe.core.database.TransformRunEntity
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.database.TranscriptEntity
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.TransformStatus
import dev.scrybe.core.model.TranscriptType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class SessionTransformCoordinator @Inject constructor(
    private val transcriptDao: TranscriptDao,
    private val transformProfileDao: TransformProfileDao,
    private val transformRunDao: TransformRunDao,
    private val transformationPipeline: TransformationPipeline,
) {

    suspend fun transformLatestRawTranscript(
        sessionId: String,
        profileId: String,
    ): Result<TranscriptEntity> {
        val inputTranscript = transcriptDao.getTranscriptsForSession(sessionId)
            .first()
            .lastOrNull { it.type == TranscriptType.RAW.name }
            ?: return Result.failure(IllegalStateException("No raw transcript available"))

        val profile = transformProfileDao.getProfileById(profileId)
            ?: return Result.failure(IllegalStateException("Profile not found"))

        val runId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        transformRunDao.insertRun(
            TransformRunEntity(
                id = runId,
                sessionId = sessionId,
                profileId = profile.id,
                inputTranscriptId = inputTranscript.id,
                outputTranscriptId = null,
                status = TransformStatus.RUNNING.name,
                errorMessage = null,
                startedAt = startedAt,
                completedAt = null,
            )
        )

        val result = transformationPipeline.execute(
            input = TransformInput(
                sessionId = sessionId,
                transcriptId = inputTranscript.id,
                rawText = inputTranscript.content,
                profileId = profile.id,
                systemPrompt = profile.systemPrompt,
            ),
            providerType = ProviderType.valueOf(profile.providerType),
        )

        return result.fold(
            onSuccess = { transformResult ->
                val transcript = TranscriptEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    content = transformResult.transformedText,
                    type = TranscriptType.TRANSFORMED.name,
                    providerType = profile.providerType,
                    transformProfileId = profile.id,
                    transformRunId = runId,
                    createdAt = System.currentTimeMillis(),
                )
                transcriptDao.insertTranscript(transcript)
                transformRunDao.insertRun(
                    TransformRunEntity(
                        id = runId,
                        sessionId = sessionId,
                        profileId = profile.id,
                        inputTranscriptId = inputTranscript.id,
                        outputTranscriptId = transcript.id,
                        status = TransformStatus.SUCCESS.name,
                        errorMessage = null,
                        startedAt = startedAt,
                        completedAt = System.currentTimeMillis(),
                    )
                )
                Result.success(transcript)
            },
            onFailure = { error ->
                transformRunDao.insertRun(
                    TransformRunEntity(
                        id = runId,
                        sessionId = sessionId,
                        profileId = profile.id,
                        inputTranscriptId = inputTranscript.id,
                        outputTranscriptId = null,
                        status = TransformStatus.FAILED.name,
                        errorMessage = error.message,
                        startedAt = startedAt,
                        completedAt = System.currentTimeMillis(),
                    )
                )
                Result.failure(error)
            },
        )
    }
}
