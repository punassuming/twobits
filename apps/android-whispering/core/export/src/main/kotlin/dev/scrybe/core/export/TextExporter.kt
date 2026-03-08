package dev.scrybe.core.export

import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.Transcript
import dev.scrybe.core.model.TranscriptType
import java.io.File
import javax.inject.Inject

class TextExporter @Inject constructor() {
    fun export(
        session: RecordingSession,
        transcripts: List<Transcript>,
        outputDir: File,
    ): Result<File> = runCatching {
        outputDir.mkdirs()
        val file = File(outputDir, "${session.id}.txt")
        val rawTranscripts = transcripts.filter { it.type == TranscriptType.RAW }
        val sb = StringBuilder()
        sb.appendLine(session.title)
        sb.appendLine()
        rawTranscripts.forEach { sb.appendLine(it.content) }
        file.writeText(sb.toString())
        file
    }
}
