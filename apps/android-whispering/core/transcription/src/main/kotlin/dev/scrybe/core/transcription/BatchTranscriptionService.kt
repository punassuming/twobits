package dev.scrybe.core.transcription

import android.util.Log
import dev.scrybe.core.database.TranscriptChunkDao
import dev.scrybe.core.database.TranscriptChunkEntity
import dev.scrybe.core.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class BatchTranscriptResult(
    val text: String,
    val isPartial: Boolean,
    val completedChunks: Int,
    val totalChunks: Int,
)

@Singleton
class BatchTranscriptionService
    @Inject
    constructor(
        private val audioChunker: OpenAiAudioChunker,
        private val chunkDao: TranscriptChunkDao,
        private val orchestrator: TranscriptionOrchestrator,
    ) {
        suspend fun transcribe(
            sessionId: String,
            audioFile: File,
            providerType: ProviderType,
            options: TranscriptionOptions = TranscriptionOptions(),
        ): Result<BatchTranscriptResult> {
            val audioChunks =
                withContext(Dispatchers.IO) {
                    runCatching { audioChunker.createChunksIfNeeded(audioFile) }
                }.getOrElse { e -> return Result.failure(e) }
            val totalChunks = audioChunks.size
            val isSingleChunk = totalChunks == 1 && audioChunks.first().absolutePath == audioFile.absolutePath

            if (isSingleChunk) {
                return orchestrator
                    .transcribe(sessionId, audioFile, providerType, options)
                    .map { result ->
                        BatchTranscriptResult(
                            text = result.text,
                            isPartial = false,
                            completedChunks = 1,
                            totalChunks = 1,
                        )
                    }
            }

            return try {
                transcribeChunks(sessionId, audioChunks, totalChunks, providerType, options)
            } finally {
                audioChunker.cleanupChunks(audioChunks, audioFile)
            }
        }

        private suspend fun transcribeChunks(
            sessionId: String,
            audioChunks: List<File>,
            totalChunks: Int,
            providerType: ProviderType,
            options: TranscriptionOptions,
        ): Result<BatchTranscriptResult> {
            for ((index, chunkFile) in audioChunks.withIndex()) {
                val existing = chunkDao.getChunkByIndex(sessionId, index)
                if (existing?.status == CHUNK_STATUS_DONE) {
                    Log.d(TAG, "Session $sessionId chunk $index already done, skipping")
                    continue
                }
                val chunkResult =
                    orchestrator.transcribe("$sessionId/chunk/$index", chunkFile, providerType, options)
                if (chunkResult.isFailure) {
                    Log.e(TAG, "Session $sessionId chunk $index failed", chunkResult.exceptionOrNull())
                    return Result.success(
                        BatchTranscriptResult(
                            text = buildPartialText(sessionId),
                            isPartial = true,
                            completedChunks =
                                chunkDao.getChunksForSession(sessionId).count { it.status == CHUNK_STATUS_DONE },
                            totalChunks = totalChunks,
                        ),
                    )
                }
                chunkDao.insertChunk(
                    TranscriptChunkEntity(
                        id = "$sessionId:$index",
                        sessionId = sessionId,
                        chunkIndex = index,
                        totalChunks = totalChunks,
                        status = CHUNK_STATUS_DONE,
                        text = chunkResult.getOrNull()?.text,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            val fullText =
                chunkDao
                    .getChunksForSession(sessionId)
                    .sortedBy { it.chunkIndex }
                    .mapNotNull { it.text }
                    .joinToString("\n\n")
            chunkDao.deleteForSession(sessionId)
            return Result.success(
                BatchTranscriptResult(text = fullText.trim(), isPartial = false, completedChunks = totalChunks, totalChunks = totalChunks),
            )
        }

        suspend fun hasPartialProgress(sessionId: String): Boolean = chunkDao.getChunksForSession(sessionId).any { it.status == CHUNK_STATUS_DONE }

        suspend fun clearProgress(sessionId: String) {
            chunkDao.deleteForSession(sessionId)
        }

        private suspend fun buildPartialText(sessionId: String): String {
            val doneParts =
                chunkDao
                    .getChunksForSession(sessionId)
                    .filter { it.status == CHUNK_STATUS_DONE }
                    .sortedBy { it.chunkIndex }
                    .mapNotNull { it.text }
            return doneParts.joinToString("\n\n")
        }

        private companion object {
            const val TAG = "BatchTranscription"
            const val CHUNK_STATUS_DONE = "DONE"
        }
    }
