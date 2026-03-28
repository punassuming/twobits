package dev.scrybe.core.export

import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.Transcript
import dev.scrybe.core.model.TranscriptType
import java.io.File
import javax.inject.Inject

class MarkdownExporter
    @Inject
    constructor() {
        fun export(
            session: RecordingSession,
            transcripts: List<Transcript>,
            outputDir: File,
        ): Result<File> =
            runCatching {
                outputDir.mkdirs()
                val file = File(outputDir, "${session.id}.md")
                val rawTranscripts = transcripts.filter { it.type == TranscriptType.RAW }
                val transformedTranscripts = transcripts.filter { it.type == TranscriptType.TRANSFORMED }
                val sb = StringBuilder()
                sb.appendLine("# ${session.title}")
                sb.appendLine()
                sb.appendLine("**Date:** ${session.createdAt}")
                sb.appendLine("**Duration:** ${session.durationMs / 1000}s")
                sb.appendLine()
                if (rawTranscripts.isNotEmpty()) {
                    sb.appendLine("## Raw Transcript")
                    sb.appendLine()
                    rawTranscripts.forEach { sb.appendLine(it.content) }
                }
                if (transformedTranscripts.isNotEmpty()) {
                    sb.appendLine("## Transformed Outputs")
                    transformedTranscripts.forEach { transcript ->
                        sb.appendLine()
                        sb.appendLine("### Transform ${transcript.transformProfileId ?: "Unknown"}")
                        sb.appendLine()
                        sb.appendLine(transcript.content)
                    }
                }
                file.writeText(sb.toString())
                file
            }
    }
