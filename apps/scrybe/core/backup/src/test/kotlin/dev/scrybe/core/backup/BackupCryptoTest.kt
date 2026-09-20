package dev.scrybe.core.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Encryption is optional, which makes it easy to under-test — the common path never touches it.
 * But it is also the one feature here that can destroy data rather than merely fail: an encrypted
 * backup that will not open is a lost recording history. So the round trip is asserted, and so is
 * every way it should refuse.
 *
 * These run on the JVM against the real `javax.crypto` providers, not stubs, so they exercise the
 * same PBKDF2 and AES-GCM implementations the device will use.
 */
class BackupCryptoTest {
    private val payload = "a recording history worth not losing".repeat(64).toByteArray()

    private fun encrypt(
        passphrase: String,
        salt: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encryptingStream(out, passphrase.toCharArray(), salt, iv).use { it.write(payload) }
        return out.toByteArray()
    }

    /** `readAuthenticated` is suspend; these tests are not, so each call gets its own scope. */
    private fun <T> read(
        cipherText: ByteArray,
        passphrase: String,
        salt: ByteArray,
        iv: ByteArray,
        block: (InputStream) -> T,
    ): T =
        runBlocking {
            BackupCrypto.readAuthenticated(ByteArrayInputStream(cipherText), passphrase.toCharArray(), salt, iv) { block(it) }
        }

    @Test
    fun `a payload encrypted with a passphrase comes back byte for byte`() {
        val salt = BackupCrypto.randomBytes(BackupContainer.SALT_BYTES)
        val iv = BackupCrypto.randomBytes(BackupContainer.IV_BYTES)
        val cipherText = encrypt("correct horse battery staple", salt, iv)

        val decrypted = read(cipherText, "correct horse battery staple", salt, iv) { it.readBytes() }

        assertArrayEquals(payload, decrypted)
    }

    @Test
    fun `the ciphertext does not contain the plaintext`() {
        val salt = BackupCrypto.randomBytes(BackupContainer.SALT_BYTES)
        val iv = BackupCrypto.randomBytes(BackupContainer.IV_BYTES)
        val cipherText = encrypt("passphrase", salt, iv)
        assertFalse(String(cipherText, Charsets.ISO_8859_1).contains("recording history"))
    }

    @Test
    fun `a wrong passphrase is refused rather than returning rubbish`() {
        val salt = BackupCrypto.randomBytes(BackupContainer.SALT_BYTES)
        val iv = BackupCrypto.randomBytes(BackupContainer.IV_BYTES)
        val cipherText = encrypt("the right one", salt, iv)

        val failure =
            assertThrows(BackupDecryptionException::class.java) {
                read(cipherText, "the wrong one", salt, iv) { it.readBytes() }
            }
        assertTrue(failure.message!!.contains("passphrase is wrong"))
    }

    /**
     * The reason for GCM over CBC. A modified backup must fail loudly, not decrypt into something
     * that looks plausible enough for restore to start inserting it.
     */
    @Test
    fun `a tampered payload fails the authentication tag`() {
        val salt = BackupCrypto.randomBytes(BackupContainer.SALT_BYTES)
        val iv = BackupCrypto.randomBytes(BackupContainer.IV_BYTES)
        val cipherText = encrypt("passphrase", salt, iv)
        cipherText[cipherText.size / 2] = (cipherText[cipherText.size / 2] + 1).toByte()

        assertThrows(BackupDecryptionException::class.java) {
            read(cipherText, "passphrase", salt, iv) { it.readBytes() }
        }
    }

    /**
     * The case that motivated draining the stream in `readAuthenticated`. GCM only checks its tag
     * at EOF, so a reader that stops early — `ZipInputStream` stops at the end-of-central-directory
     * marker, not the end of the stream — would never trigger the check. A tampered backup has to
     * fail even when the block ignores most of it.
     */
    @Test
    fun `tampering is caught even when the reader stops early`() {
        val salt = BackupCrypto.randomBytes(BackupContainer.SALT_BYTES)
        val iv = BackupCrypto.randomBytes(BackupContainer.IV_BYTES)
        val cipherText = encrypt("passphrase", salt, iv)
        cipherText[cipherText.size - 20] = (cipherText[cipherText.size - 20] + 1).toByte()

        assertThrows(BackupDecryptionException::class.java) {
            // Reads only the first few bytes and returns, exactly as a zip reader would.
            read(cipherText, "passphrase", salt, iv) { it.read(ByteArray(16)) }
        }
    }

    /**
     * The case the previous test did not cover, and the one that actually ships.
     *
     * Every real reader closes the stream it is given — `restorePayload` does
     * `ZipInputStream(...).use`. `CipherInputStream.close()` runs `doFinal` itself and discards the
     * authentication failure, so if the block could close the live stream the tag would never be
     * checked on the only path that matters, while a test whose block happens not to close would
     * still pass. This asserts tampering is caught even when the block closes.
     */
    @Test
    fun `tampering is caught even when the reader closes the stream`() {
        val salt = BackupCrypto.randomBytes(BackupContainer.SALT_BYTES)
        val iv = BackupCrypto.randomBytes(BackupContainer.IV_BYTES)
        val cipherText = encrypt("passphrase", salt, iv)
        cipherText[cipherText.size - 24] = (cipherText[cipherText.size - 24] + 1).toByte()

        assertThrows(BackupDecryptionException::class.java) {
            read(cipherText, "passphrase", salt, iv) { stream ->
                stream.read(ByteArray(16))
                stream.close()
            }
        }
    }

    /** The happy path must survive the same close, or every encrypted restore would fail. */
    @Test
    fun `an intact payload still decrypts when the reader closes the stream`() {
        val salt = BackupCrypto.randomBytes(BackupContainer.SALT_BYTES)
        val iv = BackupCrypto.randomBytes(BackupContainer.IV_BYTES)
        val cipherText = encrypt("passphrase", salt, iv)

        val first =
            read(cipherText, "passphrase", salt, iv) { stream ->
                val head = ByteArray(16)
                stream.read(head)
                stream.close()
                head
            }
        assertArrayEquals(payload.copyOf(16), first)
    }

    @Test
    fun `a truncated payload fails rather than returning a partial history`() {
        val salt = BackupCrypto.randomBytes(BackupContainer.SALT_BYTES)
        val iv = BackupCrypto.randomBytes(BackupContainer.IV_BYTES)
        val cipherText = encrypt("passphrase", salt, iv)

        assertThrows(BackupDecryptionException::class.java) {
            read(cipherText.copyOf(cipherText.size - 8), "passphrase", salt, iv) { it.readBytes() }
        }
    }

    @Test
    fun `the same passphrase with a different salt does not decrypt`() {
        val iv = BackupCrypto.randomBytes(BackupContainer.IV_BYTES)
        val cipherText = encrypt("passphrase", BackupCrypto.randomBytes(BackupContainer.SALT_BYTES), iv)

        assertThrows(BackupDecryptionException::class.java) {
            read(cipherText, "passphrase", BackupCrypto.randomBytes(BackupContainer.SALT_BYTES), iv) { it.readBytes() }
        }
    }

    @Test
    fun `random salts and ivs are the declared size and not all zero`() {
        val salt = BackupCrypto.randomBytes(BackupContainer.SALT_BYTES)
        val iv = BackupCrypto.randomBytes(BackupContainer.IV_BYTES)
        assertEquals(BackupContainer.SALT_BYTES, salt.size)
        assertEquals(BackupContainer.IV_BYTES, iv.size)
        assertFalse(salt.all { it == 0.toByte() })
        assertFalse(iv.all { it == 0.toByte() })
    }
}
