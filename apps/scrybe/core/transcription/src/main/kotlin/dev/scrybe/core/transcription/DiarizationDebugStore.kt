package dev.scrybe.core.transcription

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostic record of a diarization run: what the verbose transcription produced,
 * the exact LLM exchange, and how the assignments collapsed into segments.
 */
@Serializable
data class DiarizationDebugInfo(
    val sessionId: String,
    val runAtMs: Long,
    val model: String,
    val verboseSegmentCount: Int,
    val wordTimestampsPresent: Boolean,
    val prompt: String,
    val rawLlmResponse: String? = null,
    val assignments: List<String> = emptyList(),
    val mergedSegmentCount: Int = 0,
)

/**
 * File-backed store for the latest [DiarizationDebugInfo] per session. One small JSON
 * file per session under filesDir, overwritten on each run — deliberately not in Room
 * so diagnostic data never forces a schema migration.
 */
@Singleton
class DiarizationDebugStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val json: Json,
    ) {
        private val dir: File
            get() = File(context.filesDir, DIR_NAME).apply { mkdirs() }

        fun write(info: DiarizationDebugInfo) {
            runCatching {
                fileFor(info.sessionId).writeText(json.encodeToString(DiarizationDebugInfo.serializer(), info))
            }.onFailure { Log.w(TAG, "Failed to write debug info: ${it.javaClass.simpleName}") }
        }

        fun read(sessionId: String): DiarizationDebugInfo? =
            runCatching {
                val file = fileFor(sessionId)
                if (!file.exists()) return null
                json.decodeFromString(DiarizationDebugInfo.serializer(), file.readText())
            }.getOrNull()

        fun delete(sessionId: String) {
            runCatching { fileFor(sessionId).delete() }
        }

        private fun fileFor(sessionId: String): File = File(dir, "$sessionId.json")

        private companion object {
            const val TAG = "Diarization"
            const val DIR_NAME = "diarization_debug"
        }
    }
