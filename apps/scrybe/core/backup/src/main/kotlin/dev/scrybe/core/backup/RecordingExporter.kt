package dev.scrybe.core.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.RecordingSessionEntity
import dev.scrybe.core.model.AudioFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies every recording's audio into a folder the user picked, as plain files another app can open.
 *
 * Separate from [BackupWriter] on purpose. A backup is for coming back to Scrybe; this is for
 * leaving it — handing the audio to a different recording app, a computer, or a cloud drive. Mixing
 * them would mean one action that does neither well.
 *
 * Uses `DocumentsContract` rather than `DocumentFile`, matching `ObsidianExporter`, so no new
 * dependency is needed for a tree URI the caller has already persisted permission for.
 */
@Singleton
class RecordingExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sessionDao: RecordingSessionDao,
    ) {
        /**
         * Copies all recordings into [treeUri].
         *
         * Names each file `yyyy-MM-dd_HHmm_<title>.<ext>` so a folder listing sorts chronologically
         * and each file says what it is. The extension comes from [AudioFormat.actualExtension], not
         * from the stored path: a recording made on the old "WAV" setting is an MPEG-4 file named
         * `.wav`, and copying that name out is exactly what makes another app reject it.
         */
        suspend fun exportAll(
            treeUri: Uri,
            onProgress: (exported: Int, total: Int) -> Unit = { _, _ -> },
        ): ExportSummary {
            val sessions = sessionDao.getAllSessionsOnce()
            val parentUri =
                DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                )
            var exported = 0
            var skipped = 0
            var bytes = 0L
            val usedNames = mutableSetOf<String>()

            sessions.forEachIndexed { index, session ->
                currentCoroutineContext().ensureActive()
                val source = File(session.audioFilePath)
                if (!source.isFile) {
                    skipped++
                } else {
                    val format = session.format()
                    val name =
                        ExportFileNames.forRecording(
                            createdAtMs = session.createdAt,
                            title = session.title,
                            extension = format.actualExtension,
                            sessionId = session.id,
                            used = usedNames,
                        )
                    val target =
                        DocumentsContract.createDocument(context.contentResolver, parentUri, format.actualMimeType, name)
                    if (target == null) {
                        skipped++
                    } else {
                        context.contentResolver.openOutputStream(target)?.use { out ->
                            source.inputStream().use { it.copyTo(out) }
                        } ?: error("Could not open $name for writing")
                        exported++
                        bytes += source.length()
                    }
                }
                onProgress(index + 1, sessions.size)
            }
            return ExportSummary(exported, skipped, bytes)
        }

        private fun RecordingSessionEntity.format(): AudioFormat = runCatching { AudioFormat.valueOf(audioFormat) }.getOrDefault(AudioFormat.AAC)
    }

/**
 * Filenames for exported recordings.
 *
 * Pulled out of [RecordingExporter] so the rules can be tested without a ContentResolver. Naming is
 * where a bulk export actually goes wrong — a title with a slash in it, two recordings from the
 * same minute, a title long enough to blow the filesystem's 255-byte limit — and none of that is
 * observable from the export's return value.
 */
internal object ExportFileNames {
    // Leaves room for the 15-char timestamp, a 9-char id suffix, the extension and the separators
    // inside the 255-byte limit most filesystems impose.
    private const val MAX_TITLE_CHARS = 80

    /**
     * `yyyy-MM-dd_HHmm_<title>.<ext>`, so a folder listing sorts chronologically and each file says
     * what it is.
     *
     * [used] carries names already issued in this run. `DocumentsContract.createDocument` resolves
     * collisions against files already in the folder, but it cannot see the ones we are about to
     * create, so two same-minute recordings with one title would otherwise fight over a name.
     */
    fun forRecording(
        createdAtMs: Long,
        title: String,
        extension: String,
        sessionId: String,
        used: MutableSet<String>,
    ): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(createdAtMs))
        val base = "${stamp}_${sanitize(title)}"
        val candidate = "$base.$extension"
        if (used.add(candidate)) return candidate
        val disambiguated = "${base}_${sessionId.take(8)}.$extension"
        used.add(disambiguated)
        return disambiguated
    }

    /**
     * Anything outside letters, digits, space, dash and underscore becomes an underscore. An
     * allow-list rather than a list of forbidden characters: the set that breaks a filename differs
     * by filesystem, and SAF may be writing to any of them.
     */
    fun sanitize(title: String): String {
        val cleaned =
            title
                .map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == ' ') it else '_' }
                .joinToString("")
        return cleaned
            .trim()
            .ifBlank { "Recording" }
            .take(MAX_TITLE_CHARS)
            .trim()
    }
}

/** What an export produced, for the message shown when it finishes. */
data class ExportSummary(
    val exportedCount: Int,
    /** Recordings whose audio file was missing, or which the target folder refused. */
    val skippedCount: Int,
    val totalBytes: Long,
)
