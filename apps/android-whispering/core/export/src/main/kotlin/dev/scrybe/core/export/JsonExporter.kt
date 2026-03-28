package dev.scrybe.core.export

import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.Transcript
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

class JsonExporter
    @Inject
    constructor() {
        private val json = Json { prettyPrint = true }

        fun export(
            session: RecordingSession,
            transcripts: List<Transcript>,
            outputDir: File,
        ): Result<File> =
            runCatching {
                outputDir.mkdirs()
                val file = File(outputDir, "${session.id}.json")
                val export =
                    ExportData(
                        sessionId = session.id,
                        title = session.title,
                        createdAt = session.createdAt.toString(),
                        durationMs = session.durationMs,
                        transcripts =
                            transcripts.map { t ->
                                TranscriptData(
                                    id = t.id,
                                    type = t.type.name,
                                    content = t.content,
                                    transformProfileId = t.transformProfileId,
                                )
                            },
                    )
                file.writeText(json.encodeToString(ExportData.serializer(), export))
                file
            }

        @Serializable
        private data class ExportData(
            val sessionId: String,
            val title: String,
            val createdAt: String,
            val durationMs: Long,
            val transcripts: List<TranscriptData>,
        )

        @Serializable
        private data class TranscriptData(
            val id: String,
            val type: String,
            val content: String,
            val transformProfileId: String?,
        )
    }
