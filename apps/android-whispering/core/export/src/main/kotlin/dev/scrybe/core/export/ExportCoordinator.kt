package dev.scrybe.core.export

import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.Transcript
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat { MARKDOWN, TXT, JSON }

@Singleton
class ExportCoordinator
    @Inject
    constructor(
        private val markdownExporter: MarkdownExporter,
        private val textExporter: TextExporter,
        private val jsonExporter: JsonExporter,
    ) {
        fun export(
            session: RecordingSession,
            transcripts: List<Transcript>,
            format: ExportFormat,
            outputDir: File,
        ): Result<File> =
            when (format) {
                ExportFormat.MARKDOWN -> markdownExporter.export(session, transcripts, outputDir)
                ExportFormat.TXT -> textExporter.export(session, transcripts, outputDir)
                ExportFormat.JSON -> jsonExporter.export(session, transcripts, outputDir)
            }
    }
