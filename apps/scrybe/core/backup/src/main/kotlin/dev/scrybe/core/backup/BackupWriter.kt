package dev.scrybe.core.backup

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
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a `.scrybe` backup: the whole database plus every recording's audio.
 *
 * Streams throughout. A recording history runs to gigabytes, so audio is copied entry by entry
 * straight into the zip and never held in memory. Only the database JSON — text, and small next to
 * the audio — is built up before writing.
 */
@Singleton
class BackupWriter
    @Inject
    constructor(
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
        private val json = Json { prettyPrint = false }

        /**
         * Writes a backup to [destination], which is closed by this call.
         *
         * [passphrase] null means no encryption, which is the default. When supplied it is used to
         * derive the key and is **not** retained; the caller owns clearing it.
         *
         * [onProgress] reports sessions written out of the total, for a progress bar. It is called
         * from whatever thread this runs on.
         */
        suspend fun write(
            destination: OutputStream,
            appVersionName: String,
            databaseSchemaVersion: Int,
            passphrase: CharArray? = null,
            onProgress: (written: Int, total: Int) -> Unit = { _, _ -> },
        ): BackupSummary {
            val encrypted = passphrase != null
            val salt = if (encrypted) BackupCrypto.randomBytes(BackupContainer.SALT_BYTES) else BackupCrypto.zeroBytes(BackupContainer.SALT_BYTES)
            val iv = if (encrypted) BackupCrypto.randomBytes(BackupContainer.IV_BYTES) else BackupCrypto.zeroBytes(BackupContainer.IV_BYTES)

            return destination.use { raw ->
                BackupContainer.writeHeader(
                    raw,
                    BackupHeader(BackupContainer.CURRENT_VERSION, encrypted, salt, iv),
                )
                // The header is written to the raw stream first and stays in the clear; only what
                // follows goes through the cipher. Restore depends on that to know it needs to ask
                // for a passphrase at all.
                val payload = if (passphrase != null) BackupCrypto.encryptingStream(raw, passphrase, salt, iv) else raw
                payload.use { writePayload(it, appVersionName, databaseSchemaVersion, onProgress) }
            }
        }

        private suspend fun writePayload(
            payload: OutputStream,
            appVersionName: String,
            databaseSchemaVersion: Int,
            onProgress: (Int, Int) -> Unit,
        ): BackupSummary {
            val database = readDatabase()
            val sessions = database.sessions
            var audioCount = 0
            var audioBytes = 0L

            ZipOutputStream(payload).use { zip ->
                zip.putNextEntry(ZipEntry(BackupEntryNames.DATABASE))
                zip.write(json.encodeToString(DatabaseDto.serializer(), database).toByteArray())
                zip.closeEntry()

                sessions.forEachIndexed { index, session ->
                    // A multi-gigabyte backup must stay cancellable; without this a "cancel" tap
                    // would do nothing until every file had been copied.
                    currentCoroutineContext().ensureActive()
                    val source = File(session.audioFilePath)
                    if (source.isFile) {
                        val extension = source.extension.ifBlank { "m4a" }
                        zip.putNextEntry(ZipEntry(BackupEntryNames.audioEntry(session.id, extension)))
                        source.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        audioCount++
                        audioBytes += source.length()
                    }
                    // A session whose audio has gone missing is still backed up — its transcript and
                    // metadata are worth keeping, and restore handles the absent file.
                    onProgress(index + 1, sessions.size)
                }

                // Written last so its counts describe what actually went in, not what was intended.
                zip.putNextEntry(ZipEntry(BackupEntryNames.MANIFEST))
                val manifest =
                    BackupManifest(
                        formatVersion = BackupContainer.CURRENT_VERSION,
                        appVersionName = appVersionName,
                        databaseSchemaVersion = databaseSchemaVersion,
                        createdAtMs = System.currentTimeMillis(),
                        sessionCount = sessions.size,
                        audioFileCount = audioCount,
                        totalAudioBytes = audioBytes,
                    )
                zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                zip.closeEntry()
            }

            return BackupSummary(
                sessionCount = sessions.size,
                audioFileCount = audioCount,
                missingAudioCount = sessions.size - audioCount,
                totalAudioBytes = audioBytes,
            )
        }

        private suspend fun readDatabase(): DatabaseDto =
            DatabaseDto(
                sessions = sessionDao.getAllSessionsOnce().map { it.toDto() },
                transcripts = transcriptDao.getAllTranscriptsOnce().map { it.toDto() },
                transcriptChunks = chunkDao.getAllChunksOnce().map { it.toDto() },
                speakerSegments = segmentDao.getAllSegmentsOnce().map { it.toDto() },
                sessionTasks = taskDao.getAllTasksOnce().map { it.toDto() },
                transformRuns = transformRunDao.getAllRunsOnce().map { it.toDto() },
                transformProfiles = transformProfileDao.getAllProfilesOnce().map { it.toDto() },
                folders = folderDao.getAllFoldersOnce().map { it.toDto() },
                persons = personDao.getAllPersonsOnce().map { it.toDto() },
                customRecordingTypes = customTypeDao.getAllOnce().map { it.toDto() },
                providerConfigs = providerConfigDao.getAllProviderConfigsOnce().map { it.toDto() },
            )
    }

/** What a finished backup contains, for the message shown when it completes. */
data class BackupSummary(
    val sessionCount: Int,
    val audioFileCount: Int,
    /** Sessions whose audio file was already gone from disk. Their metadata is still backed up. */
    val missingAudioCount: Int,
    val totalAudioBytes: Long,
)
