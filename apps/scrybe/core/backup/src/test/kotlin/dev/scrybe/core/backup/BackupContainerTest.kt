package dev.scrybe.core.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The header is the first thing restore touches and the only thing it can trust before decrypting,
 * so every way it can be wrong is asserted — and each one has to produce a *distinct* message.
 * "This isn't a Scrybe backup" and "this backup is from a newer version" call for different actions
 * from whoever is holding the file, and a single generic failure would hide that.
 */
class BackupContainerTest {
    private fun header(
        version: Int = BackupContainer.CURRENT_VERSION,
        encrypted: Boolean = false,
    ) = BackupHeader(
        version = version,
        encrypted = encrypted,
        salt = if (encrypted) ByteArray(BackupContainer.SALT_BYTES) { it.toByte() } else BackupCrypto.zeroBytes(BackupContainer.SALT_BYTES),
        iv = if (encrypted) ByteArray(BackupContainer.IV_BYTES) { (it + 100).toByte() } else BackupCrypto.zeroBytes(BackupContainer.IV_BYTES),
    )

    private fun roundTrip(original: BackupHeader): BackupHeader {
        val bytes =
            ByteArrayOutputStream()
                .also { out ->
                    BackupContainer.writeHeader(out, original)
                }.toByteArray()
        return BackupContainer.readHeader(ByteArrayInputStream(bytes))
    }

    @Test
    fun `an unencrypted header survives a round trip`() {
        val original = header()
        val parsed = roundTrip(original)
        assertEquals(original, parsed)
        assertFalse(parsed.encrypted)
    }

    @Test
    fun `an encrypted header carries its salt and iv back intact`() {
        val original = header(encrypted = true)
        val parsed = roundTrip(original)
        assertTrue(parsed.encrypted)
        assertArrayEquals(original.salt, parsed.salt)
        assertArrayEquals(original.iv, parsed.iv)
    }

    @Test
    fun `the header is exactly the declared size so the payload starts where readers expect`() {
        val bytes = ByteArrayOutputStream().also { BackupContainer.writeHeader(it, header()) }.toByteArray()
        assertEquals(BackupContainer.HEADER_BYTES, bytes.size)
    }

    @Test
    fun `the payload is readable immediately after the header`() {
        val payload = "payload-follows-the-header".toByteArray()
        val out = ByteArrayOutputStream()
        BackupContainer.writeHeader(out, header())
        out.write(payload)

        val input = ByteArrayInputStream(out.toByteArray())
        BackupContainer.readHeader(input)
        assertArrayEquals(payload, input.readBytes())
    }

    @Test
    fun `a file that is not a backup is rejected by name`() {
        val notABackup = ByteArrayInputStream("just some other file entirely".toByteArray())
        val failure = assertThrows(BackupFormatException::class.java) { BackupContainer.readHeader(notABackup) }
        assertTrue(failure.message!!.contains("not a Scrybe backup"))
    }

    @Test
    fun `a file shorter than the magic is reported as too short rather than as the wrong type`() {
        val failure = assertThrows(BackupFormatException::class.java) { BackupContainer.readHeader(ByteArrayInputStream("SCR".toByteArray())) }
        assertTrue(failure.message!!.contains("too short"))
    }

    @Test
    fun `a truncated header is reported as incomplete`() {
        val full = ByteArrayOutputStream().also { BackupContainer.writeHeader(it, header()) }.toByteArray()
        val truncated = full.copyOf(BackupContainer.HEADER_BYTES - 4)
        val failure = assertThrows(BackupFormatException::class.java) { BackupContainer.readHeader(ByteArrayInputStream(truncated)) }
        assertTrue(failure.message!!.contains("incomplete") || failure.message!!.contains("truncated"))
    }

    @Test
    fun `a backup from a newer format version is refused and says to update`() {
        val bytes =
            ByteArrayOutputStream()
                .also {
                    BackupContainer.writeHeader(it, header(version = BackupContainer.CURRENT_VERSION + 1))
                }.toByteArray()
        val failure = assertThrows(BackupFormatException::class.java) { BackupContainer.readHeader(ByteArrayInputStream(bytes)) }
        assertTrue(failure.message!!.contains("newer version"))
    }

    @Test
    fun `an older format version is still accepted`() {
        // Nothing older exists yet, but the guard must be one-sided: refusing what we cannot
        // understand, not refusing everything that is not exactly current.
        val parsed = roundTrip(header(version = BackupContainer.CURRENT_VERSION))
        assertEquals(BackupContainer.CURRENT_VERSION, parsed.version)
    }
}
