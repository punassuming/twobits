package dev.scrybe.core.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * The on-disk envelope for a `.scrybe` backup.
 *
 * A fixed-size plaintext header followed by a payload. The header is **never** encrypted, even when
 * the payload is: restore has to be able to say "this file needs a passphrase" and prompt for one,
 * rather than asking for a passphrase up front or failing with an unintelligible decode error. The
 * header carries nothing sensitive — a magic string, two version numbers, a flag, and the salt and
 * IV, all of which are meant to be public in any sane AES-GCM design.
 *
 * Layout, big-endian, [HEADER_BYTES] total:
 *
 * ```
 * offset  size  field
 *      0     8  magic "SCRYBEBK"
 *      8     2  container format version
 *     10     2  flags — bit 0 set means the payload is AES-GCM encrypted
 *     12    16  PBKDF2 salt, all zeroes when unencrypted
 *     28    12  AES-GCM IV, all zeroes when unencrypted
 * ```
 *
 * The payload is a zip stream. Everything is written and read incrementally — a recording history
 * runs to gigabytes, so no path here may hold a whole file, let alone the whole archive, in memory.
 */
internal object BackupContainer {
    /** Identifies the file before anything else is trusted. Eight bytes, no terminator. */
    val MAGIC = "SCRYBEBK".toByteArray(Charsets.US_ASCII)

    /**
     * Bumped only for a change the current reader cannot handle. Adding a field to a DTO is not
     * such a change — the JSON inside tolerates that by itself (see [BackupDto]) — so this should
     * move very rarely.
     */
    const val CURRENT_VERSION: Int = 1

    const val FLAG_ENCRYPTED: Int = 1

    const val SALT_BYTES: Int = 16
    const val IV_BYTES: Int = 12
    const val HEADER_BYTES: Int = 8 + 2 + 2 + SALT_BYTES + IV_BYTES

    fun writeHeader(
        out: OutputStream,
        header: BackupHeader,
    ) {
        require(header.salt.size == SALT_BYTES) { "salt must be $SALT_BYTES bytes" }
        require(header.iv.size == IV_BYTES) { "iv must be $IV_BYTES bytes" }
        // Not wrapped in use {}: the caller owns the stream and keeps writing the payload to it.
        val data = DataOutputStream(out)
        data.write(MAGIC)
        data.writeShort(header.version)
        data.writeShort(if (header.encrypted) FLAG_ENCRYPTED else 0)
        data.write(header.salt)
        data.write(header.iv)
        data.flush()
    }

    /**
     * Reads and validates the header, leaving [input] positioned at the payload.
     *
     * Throws [BackupFormatException] rather than returning null for every rejection, because each
     * one means something different to the person holding the file — "this isn't a Scrybe backup"
     * and "this backup is from a newer version" need different answers, and a null cannot say which.
     */
    fun readHeader(input: InputStream): BackupHeader {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        try {
            data.readFully(magic)
        } catch (eof: EOFException) {
            throw BackupFormatException("This file is too short to be a Scrybe backup.", eof)
        }
        if (!magic.contentEquals(MAGIC)) {
            throw BackupFormatException("This is not a Scrybe backup file.")
        }
        val version: Int
        val flags: Int
        val salt = ByteArray(SALT_BYTES)
        val iv = ByteArray(IV_BYTES)
        try {
            version = data.readUnsignedShort()
            flags = data.readUnsignedShort()
            data.readFully(salt)
            data.readFully(iv)
        } catch (eof: EOFException) {
            throw BackupFormatException("This backup file is incomplete or was truncated.", eof)
        }
        if (version > CURRENT_VERSION) {
            throw BackupFormatException(
                "This backup was made by a newer version of Scrybe (format $version). " +
                    "Update the app and try again.",
            )
        }
        return BackupHeader(
            version = version,
            encrypted = flags and FLAG_ENCRYPTED != 0,
            salt = salt,
            iv = iv,
        )
    }
}

/** Parsed [BackupContainer] header. [salt] and [iv] are all zeroes when [encrypted] is false. */
internal data class BackupHeader(
    val version: Int,
    val encrypted: Boolean,
    val salt: ByteArray,
    val iv: ByteArray,
) {
    // Arrays use identity equality, which makes the generated data-class equals() useless and the
    // hashCode() unstable. Both are overridden rather than dropping `data`, since the copy() and
    // toString() are worth keeping.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupHeader) return false
        return version == other.version &&
            encrypted == other.encrypted &&
            salt.contentEquals(other.salt) &&
            iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + encrypted.hashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}

/**
 * The file is not a backup this build can read. The message is shown to the user as-is, so it says
 * what to do about it rather than naming the field that failed to parse.
 */
class BackupFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The payload could not be decrypted — wrong passphrase, or the file has been altered. */
class BackupDecryptionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
