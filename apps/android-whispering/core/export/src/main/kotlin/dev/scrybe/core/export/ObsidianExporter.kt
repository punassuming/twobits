package dev.scrybe.core.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.common.sanitizeFileName
import dev.scrybe.core.model.RecordingSession
import dev.scrybe.core.model.Transcript
import dev.scrybe.core.model.TranscriptType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObsidianExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun export(
            session: RecordingSession,
            transcripts: List<Transcript>,
            vaultUri: String,
        ): Result<String> =
            runCatching {
                val treeUri = Uri.parse(vaultUri)
                val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
                val fileName = "${sanitizeFileName(session.title)}.md"
                val docUri =
                    DocumentsContract.createDocument(
                        context.contentResolver,
                        parentUri,
                        "text/markdown",
                        fileName,
                    ) ?: error("Could not create file in vault")
                context.contentResolver.openOutputStream(docUri)?.use { out ->
                    out.write(buildMarkdown(session, transcripts).toByteArray())
                } ?: error("Could not open output stream")
                fileName
            }

        private fun buildMarkdown(
            session: RecordingSession,
            transcripts: List<Transcript>,
        ): String {
            val date =
                session.createdAt
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val duration =
                session.durationMs.let { ms ->
                    val m = ms / 60_000
                    val s = (ms % 60_000) / 1000
                    "%d:%02d".format(m, s)
                }
            val tagLine = if (session.tags.isEmpty()) "" else session.tags.joinToString(" ") { "#$it" }
            val sb = StringBuilder()
            sb.appendLine("---")
            sb.appendLine("title: \"${session.title}\"")
            sb.appendLine("date: $date")
            sb.appendLine("mode: ${session.mode.name.lowercase()}")
            sb.appendLine("duration: $duration")
            session.locationLabel?.let { sb.appendLine("location: \"$it\"") }
            if (session.tags.isNotEmpty()) {
                sb.appendLine("tags: [${session.tags.joinToString(", ")}]")
            }
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine("# ${session.title}")
            if (tagLine.isNotBlank()) {
                sb.appendLine()
                sb.appendLine(tagLine)
            }
            val rawTranscripts = transcripts.filter { it.type == TranscriptType.RAW }
            val transformedTranscripts = transcripts.filter { it.type == TranscriptType.TRANSFORMED }
            if (rawTranscripts.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("## Transcript")
                sb.appendLine()
                rawTranscripts.forEach { sb.appendLine(it.content) }
            }
            if (transformedTranscripts.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("## Output")
                transformedTranscripts.forEach { sb.appendLine(it.content) }
            }
            return sb.toString()
        }
    }
