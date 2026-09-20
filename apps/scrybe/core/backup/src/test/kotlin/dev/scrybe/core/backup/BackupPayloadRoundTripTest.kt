package dev.scrybe.core.backup

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Exercises the payload the way `BackupWriter` writes it and `BackupReader` reads it, without Room
 * or a Context.
 *
 * `BackupWriter` and `BackupReader` both need a database and an Android `Context`, and this repo has
 * no Robolectric or mocking library, so neither can be instantiated in a unit test. What *can* be
 * tested is the thing between them: the zip layout, the header, the encryption wrapper and the entry
 * naming. That is where a format goes wrong; the DAO calls either side are straightforward.
 */
class BackupPayloadRoundTripTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val database =
        DatabaseDto(
            sessions =
                listOf(
                    SessionDto(id = "session-one", title = "Standup", createdAt = 1_750_000_000_000),
                    SessionDto(id = "session-two", title = "Retro", createdAt = 1_750_000_100_000),
                ),
            transcripts = listOf(TranscriptDto(id = "t1", sessionId = "session-one", content = "morning")),
            folders = listOf(FolderDto(id = "f1", name = "Work")),
        )

    private val audio = mapOf("session-one" to "AUDIO-ONE-BYTES".toByteArray(), "session-two" to "AUDIO-TWO".toByteArray())

    /** Builds a container the same way [BackupWriter] does, so the reader is tested against it. */
    private fun writeContainer(passphrase: CharArray? = null): ByteArray {
        val encrypted = passphrase != null
        val salt = if (encrypted) BackupCrypto.randomBytes(BackupContainer.SALT_BYTES) else BackupCrypto.zeroBytes(BackupContainer.SALT_BYTES)
        val iv = if (encrypted) BackupCrypto.randomBytes(BackupContainer.IV_BYTES) else BackupCrypto.zeroBytes(BackupContainer.IV_BYTES)
        val out = ByteArrayOutputStream()
        BackupContainer.writeHeader(out, BackupHeader(BackupContainer.CURRENT_VERSION, encrypted, salt, iv))
        val payload = if (passphrase != null) BackupCrypto.encryptingStream(out, passphrase, salt, iv) else out
        payload.use { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.putNextEntry(ZipEntry(BackupEntryNames.DATABASE))
                zip.write(json.encodeToString(DatabaseDto.serializer(), database).toByteArray())
                zip.closeEntry()
                audio.forEach { (sessionId, bytes) ->
                    zip.putNextEntry(ZipEntry(BackupEntryNames.audioEntry(sessionId, "m4a")))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry(BackupEntryNames.MANIFEST))
                val manifest =
                    BackupManifest(
                        formatVersion = BackupContainer.CURRENT_VERSION,
                        databaseSchemaVersion = 17,
                        sessionCount = database.sessions.size,
                        audioFileCount = audio.size,
                    )
                zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Mirrors the reader's single streaming pass over the payload. */
    private fun readPayload(stream: java.io.InputStream): Triple<DatabaseDto?, BackupManifest?, Map<String, ByteArray>> {
        var db: DatabaseDto? = null
        var manifest: BackupManifest? = null
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == BackupEntryNames.DATABASE -> db = json.decodeFromString(DatabaseDto.serializer(), zip.readBytes().decodeToString())
                    entry.name == BackupEntryNames.MANIFEST -> manifest = json.decodeFromString(BackupManifest.serializer(), zip.readBytes().decodeToString())
                    else -> BackupEntryNames.sessionIdOfAudioEntry(entry.name)?.let { files[it] = zip.readBytes() }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return Triple(db, manifest, files)
    }

    @Test
    fun `an unencrypted backup round trips with its database audio and manifest intact`() {
        val container = ByteArrayInputStream(writeContainer())
        val header = BackupContainer.readHeader(container)
        assertEquals(false, header.encrypted)

        val (db, manifest, files) = readPayload(container)
        assertEquals(2, db!!.sessions.size)
        assertEquals("Standup", db.sessions.first().title)
        assertEquals("Work", db.folders.single().name)
        assertEquals(17, manifest!!.databaseSchemaVersion)
        assertEquals(2, files.size)
        assertEquals("AUDIO-ONE-BYTES", files.getValue("session-one").decodeToString())
    }

    @Test
    fun `an encrypted backup round trips with the right passphrase`() {
        val container = ByteArrayInputStream(writeContainer("a good passphrase".toCharArray()))
        val header = BackupContainer.readHeader(container)
        assertTrue(header.encrypted)

        val result =
            runBlocking {
                BackupCrypto.readAuthenticated(container, "a good passphrase".toCharArray(), header.salt, header.iv) { readPayload(it) }
            }
        assertEquals(2, result.first!!.sessions.size)
        assertEquals("AUDIO-TWO", result.third.getValue("session-two").decodeToString())
    }

    @Test
    fun `an encrypted backup is refused with the wrong passphrase`() {
        val container = ByteArrayInputStream(writeContainer("the right one".toCharArray()))
        val header = BackupContainer.readHeader(container)
        assertThrows(BackupDecryptionException::class.java) {
            runBlocking {
                BackupCrypto.readAuthenticated(container, "not it".toCharArray(), header.salt, header.iv) { readPayload(it) }
            }
        }
    }

    /**
     * The header must stay readable when the payload is not, so the UI can prompt for a passphrase
     * rather than failing with a decode error the user cannot act on.
     */
    @Test
    fun `an encrypted backup announces itself before any passphrase is needed`() {
        val header = BackupContainer.readHeader(ByteArrayInputStream(writeContainer("secret".toCharArray())))
        assertTrue(header.encrypted)
        assertTrue("salt should be random, not zeroes", header.salt.any { it != 0.toByte() })
    }

    /** A hostile entry is skipped, and the legitimate content around it still restores. */
    @Test
    fun `an entry with a traversing name is ignored rather than extracted`() {
        val out = ByteArrayOutputStream()
        BackupContainer.writeHeader(
            out,
            BackupHeader(
                BackupContainer.CURRENT_VERSION,
                false,
                BackupCrypto.zeroBytes(BackupContainer.SALT_BYTES),
                BackupCrypto.zeroBytes(BackupContainer.IV_BYTES),
            ),
        )
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("audio/../../databases/scrybe-db"))
            zip.write("evil".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BackupEntryNames.DATABASE))
            zip.write(json.encodeToString(DatabaseDto.serializer(), database).toByteArray())
            zip.closeEntry()
        }

        val container = ByteArrayInputStream(out.toByteArray())
        BackupContainer.readHeader(container)
        val (db, _, files) = readPayload(container)

        assertTrue("the traversing entry must not be treated as audio", files.isEmpty())
        assertEquals("the rest of the backup must still restore", 2, db!!.sessions.size)
    }

    @Test
    fun `a backup with no audio still carries its database`() {
        val out = ByteArrayOutputStream()
        BackupContainer.writeHeader(
            out,
            BackupHeader(
                BackupContainer.CURRENT_VERSION,
                false,
                BackupCrypto.zeroBytes(BackupContainer.SALT_BYTES),
                BackupCrypto.zeroBytes(BackupContainer.IV_BYTES),
            ),
        )
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(BackupEntryNames.DATABASE))
            zip.write("""{"sessions":[{"id":"only-metadata"}]}""".toByteArray())
            zip.closeEntry()
        }
        val container = ByteArrayInputStream(out.toByteArray())
        BackupContainer.readHeader(container)
        val (db, manifest, files) = readPayload(container)
        assertEquals("only-metadata", db!!.sessions.single().id)
        assertTrue(files.isEmpty())
        assertNull(manifest)
    }
}
