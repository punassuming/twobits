package dev.scrybe.core.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.database.CustomRecordingTypeDao
import dev.scrybe.core.database.FolderDao
import dev.scrybe.core.database.PersonDao
import dev.scrybe.core.database.ProviderConfigDao
import dev.scrybe.core.database.RecordingSessionDao
import dev.scrybe.core.database.SessionTaskDao
import dev.scrybe.core.database.SpeakerSegmentDao
import dev.scrybe.core.database.TranscriptChunkDao
import dev.scrybe.core.database.TranscriptDao
import dev.scrybe.core.database.TransformProfileDao
import dev.scrybe.core.database.TransformRunDao
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a `.scrybe` backup and merges it into the database on this device.
 *
 * Merge, not replace: a session already present is left exactly as it is. That makes restore
 * idempotent — running it twice changes nothing the second time — and safe on a phone that has
 * already been recording before the backup was restored.
 *
 * Two things this has to get right that a naive implementation would not:
 *
 * - **Paths.** `recording_sessions.audioFilePath` is absolute and belongs to the device that wrote
 *   the backup. It is rewritten to this device's recordings directory before insert.
 * - **Trust.** The archive comes from the user, so entry names are validated by allow-list
 *   ([BackupEntryNames]) rather than used. `LocalModelManager.extractTarBz2` writes
 *   `File(destDir, entry.name)` unchecked; that is fine for an archive from a known URL and not
 *   fine here.
 */
@Singleton
class BackupReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sessionDao: RecordingSessionDao,
        private val transcriptDao: TranscriptDao,
        private val chunkDao: TranscriptChunkDao,
        private val segmentDao: SpeakerSegmentDao,
        private val taskDao: SessionTaskDao,
        private val transformRunDao: TransformRunDao,
        private val transformProfileDao: TransformProfileDao,
        private val folderDao: FolderDao,
        private val personDao: PersonDao,
        private val customTypeDao: CustomRecordingTypeDao,
        private val providerConfigDao: ProviderConfigDao,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        /**
         * Whether [source] is a passphrase-protected backup, read from the header alone so the UI
         * can prompt before any work starts. [source] is closed by this call.
         *
         * Returns a `Boolean` rather than the parsed header because `BackupHeader` is `internal` to
         * this module — it carries the salt and IV, which nothing outside has any use for — and a
         * public function cannot expose an internal type. Throws [BackupFormatException] if the file
         * is not a backup this build can read, which is worth surfacing before the user picks a
         * passphrase.
         */
        fun isProtected(source: InputStream): Boolean = source.use { BackupContainer.readHeader(it).encrypted }

        /**
         * Restores from [source], which is closed by this call.
         *
         * Throws [BackupFormatException] for a file this build cannot read and
         * [BackupDecryptionException] for a wrong passphrase or a tampered payload — both carry
         * messages meant to be shown as-is.
         */
        suspend fun restore(
            source: InputStream,
            currentDatabaseSchemaVersion: Int,
            passphrase: CharArray? = null,
            onProgress: (restored: Int, total: Int) -> Unit = { _, _ -> },
        ): RestoreSummary =
            source.use { raw ->
                val header = BackupContainer.readHeader(raw)
                if (header.encrypted && passphrase == null) {
                    throw BackupFormatException("This backup is protected. Enter its passphrase to restore it.")
                }
                // A passphrase supplied for an unencrypted backup is simply unused — the header
                // is the authority on whether the payload is encrypted, not what the user typed.
                if (header.encrypted && passphrase != null) {
                    BackupCrypto.readAuthenticated(raw, passphrase, header.salt, header.iv) {
                        restorePayload(it, currentDatabaseSchemaVersion, onProgress)
                    }
                } else {
                    restorePayload(raw, currentDatabaseSchemaVersion, onProgress)
                }
            }

        private suspend fun restorePayload(
            payload: InputStream,
            currentDatabaseSchemaVersion: Int,
            onProgress: (Int, Int) -> Unit,
        ): RestoreSummary {
            val recordingsDir = File(context.filesDir, RECORDINGS_DIR).apply { mkdirs() }
            var database: DatabaseDto? = null
            var manifest: BackupManifest? = null
            val restoredAudio = mutableMapOf<String, String>()
            var audioBytes = 0L

            // One pass. The zip is read as a stream rather than opened as a file because the source
            // is a content URI, which has no seekable path — so audio may arrive before the database
            // entry that describes it, and the two are reconciled after the loop.
            ZipInputStream(BufferedInputStream(payload)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()
                    // Bound to a val rather than smart-casting the loop variable: `entry` is
                    // reassigned at the bottom of the loop, and a non-null smart cast across a
                    // reassigned var is the kind of thing that compiles today and stops compiling
                    // after an unrelated refactor.
                    val name = entry.name
                    when {
                        name == BackupEntryNames.DATABASE ->
                            database = json.decodeFromString(DatabaseDto.serializer(), zip.readBytes().decodeToString())
                        name == BackupEntryNames.MANIFEST ->
                            manifest = json.decodeFromString(BackupManifest.serializer(), zip.readBytes().decodeToString())
                        else -> {
                            val sessionId = BackupEntryNames.sessionIdOfAudioEntry(name)
                            if (sessionId != null) {
                                val extension = name.substringAfterLast('.')
                                val target = File(recordingsDir, "restored_$sessionId.$extension")
                                if (!target.exists()) {
                                    target.outputStream().use { out -> zip.copyTo(out) }
                                }
                                restoredAudio[sessionId] = target.absolutePath
                                audioBytes += target.length()
                            }
                            // Anything else is skipped in silence. A stray entry should not fail a
                            // restore that is otherwise recoverable.
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val decoded = database ?: throw BackupFormatException("This backup has no database in it and cannot be restored.")
            val schemaVersion = manifest?.databaseSchemaVersion ?: 0
            if (schemaVersion > currentDatabaseSchemaVersion) {
                throw BackupFormatException(
                    "This backup was made by a newer version of Scrybe. Update the app, then restore it.",
                )
            }
            return insert(decoded, restoredAudio, audioBytes, onProgress)
        }

        private suspend fun insert(
            database: DatabaseDto,
            restoredAudio: Map<String, String>,
            audioBytes: Long,
            onProgress: (Int, Int) -> Unit,
        ): RestoreSummary {
            // Standalone tables first. Sessions reference folders and custom types by plain string
            // columns with no foreign key, so nothing enforces this ordering — but a session
            // restored before its folder would point at one that does not exist yet.
            //
            // Existing ids are read once per table rather than queried per row: a history with
            // hundreds of sessions and a handful of folders should not cost hundreds of queries.
            val existingFolders = folderDao.getAllFoldersOnce().map { it.id }.toSet()
            database.folders.filterNot { it.id in existingFolders }.forEach { folderDao.insertFolder(it.toEntity()) }

            val existingPersons = personDao.getAllPersonsOnce().map { it.id }.toSet()
            database.persons.filterNot { it.id in existingPersons }.forEach { personDao.insertPerson(it.toEntity()) }

            val existingProfiles = transformProfileDao.getAllProfilesOnce().map { it.id }.toSet()
            database.transformProfiles
                .filterNot { it.id in existingProfiles }
                .forEach { transformProfileDao.insertProfile(it.toEntity()) }

            val existingTypes = customTypeDao.getAllOnce().map { it.id }.toSet()
            database.customRecordingTypes.filterNot { it.id in existingTypes }.forEach { customTypeDao.insert(it.toEntity()) }

            val existingIds = sessionDao.getAllSessionsOnce().map { it.id }.toSet()
            val incoming = database.sessions.filterNot { it.id in existingIds }
            val insertedIds = mutableSetOf<String>()
            var missingAudio = 0

            incoming.forEachIndexed { index, dto ->
                currentCoroutineContext().ensureActive()
                val path = restoredAudio[dto.id]
                if (path == null) missingAudio++
                // A session whose audio is missing is still restored: its transcript, title and date
                // are worth keeping, and the app already tolerates a row whose file has gone —
                // SessionTranscriptionCoordinator checks and reports it.
                sessionDao.insertSession(dto.toEntity(audioFilePath = path ?: dto.audioFilePath))
                insertedIds += dto.id
                onProgress(index + 1, incoming.size)
            }

            // Child rows only for sessions actually inserted. The first four cascade from
            // recording_sessions, but transform_runs has no foreign key, so a run whose session was
            // skipped would linger forever with nothing to delete it.
            database.transcripts.filter { it.sessionId in insertedIds }.forEach { transcriptDao.insertTranscript(it.toEntity()) }
            database.transcriptChunks.filter { it.sessionId in insertedIds }.forEach { chunkDao.insertChunk(it.toEntity()) }
            segmentDao.insertSegments(database.speakerSegments.filter { it.sessionId in insertedIds }.map { it.toEntity() })
            taskDao.insertTasks(database.sessionTasks.filter { it.sessionId in insertedIds }.map { it.toEntity() })
            database.transformRuns.filter { it.sessionId in insertedIds }.forEach { transformRunDao.insertRun(it.toEntity()) }

            val existingProviders = providerConfigDao.getAllProviderConfigsOnce().map { it.providerType }.toSet()
            val newProviders = database.providerConfigs.filterNot { it.providerType in existingProviders }
            newProviders.forEach { providerConfigDao.insertProviderConfig(it.toEntity()) }

            return RestoreSummary(
                sessionsRestored = insertedIds.size,
                sessionsAlreadyPresent = database.sessions.size - incoming.size,
                audioFilesRestored = insertedIds.count { it in restoredAudio },
                sessionsMissingAudio = missingAudio,
                totalAudioBytes = audioBytes,
                providersNeedingKeys = newProviders.count { it.isEnabled },
            )
        }

        private companion object {
            const val RECORDINGS_DIR = "recordings"
        }
    }

/** What a restore did, for the message shown when it finishes. */
data class RestoreSummary(
    val sessionsRestored: Int,
    /** Sessions in the backup that were already on this device, and so left untouched. */
    val sessionsAlreadyPresent: Int,
    val audioFilesRestored: Int,
    /** Restored sessions whose audio was not in the backup — metadata only. */
    val sessionsMissingAudio: Int,
    val totalAudioBytes: Long,
    /** Enabled providers restored without their API keys, which do not travel in a backup. */
    val providersNeedingKeys: Int,
)
