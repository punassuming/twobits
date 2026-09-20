package dev.scrybe.core.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Optional passphrase protection for a backup payload.
 *
 * AES-GCM, with the key derived from the passphrase by PBKDF2WithHmacSHA256. GCM is chosen over CBC
 * because it authenticates as well as encrypts: a truncated or edited backup fails on the tag
 * instead of decrypting into plausible-looking rubbish that restore would then try to parse.
 *
 * Everything here is in the JDK at minSdk 26 — no new dependency, and nothing device-specific.
 * That last part is the point: a key tied to the Android Keystore would be unrecoverable on the new
 * phone, which is the one device a migration backup has to open on.
 *
 * **There is no recovery for a forgotten passphrase.** The salt and IV live in the plaintext header
 * precisely so a correct passphrase always works, but nothing here can reconstruct a lost one. The
 * UI says so before the box is ticked.
 */
internal object BackupCrypto {
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    /**
     * Deliberately high, and the reason a backup takes a moment to start. PBKDF2 is only as good as
     * its iteration count, and this runs once per backup or restore rather than per item, so the
     * cost is paid where nobody notices it.
     */
    private const val ITERATIONS = 210_000

    private val secureRandom = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    fun zeroBytes(size: Int): ByteArray = ByteArray(size)

    /**
     * Wraps [out] so everything written to it is encrypted.
     *
     * Closing the returned stream finalises the GCM tag, so the caller must close it — and must not
     * close the underlying stream first. [CipherOutputStream.close] handles that ordering itself.
     */
    fun encryptingStream(
        out: OutputStream,
        passphrase: CharArray,
        salt: ByteArray,
        iv: ByteArray,
    ): OutputStream {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return CipherOutputStream(out, cipher)
    }

    /**
     * Wraps [input] so reads come back decrypted.
     *
     * A wrong passphrase does not fail here — it fails when the GCM tag is checked at the end of the
     * stream, which surfaces as an `IOException` from a later read rather than an exception from
     * this call. [readAuthenticated] exists so callers do not have to know that.
     */
    fun decryptingStream(
        input: InputStream,
        passphrase: CharArray,
        salt: ByteArray,
        iv: ByteArray,
    ): InputStream {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return CipherInputStream(input, cipher)
    }

    /**
     * Runs [block] over a decrypting stream, converting the two ways a bad passphrase shows up into
     * one [BackupDecryptionException].
     *
     * GCM reports tampering and a wrong key identically, on purpose — neither this code nor an
     * attacker can tell them apart — so the message covers both rather than guessing.
     */
    fun <T> readAuthenticated(
        input: InputStream,
        passphrase: CharArray,
        salt: ByteArray,
        iv: ByteArray,
        block: (InputStream) -> T,
    ): T =
        try {
            decryptingStream(input, passphrase, salt, iv).use(block)
        } catch (security: GeneralSecurityException) {
            throw BackupDecryptionException(WRONG_PASSPHRASE_MESSAGE, security)
        } catch (io: java.io.IOException) {
            // CipherInputStream wraps AEADBadTagException in an IOException, so a wrong passphrase
            // arrives here rather than above. A genuine read error looks the same from outside;
            // both mean "this backup could not be read", which is what the message says.
            throw BackupDecryptionException(WRONG_PASSPHRASE_MESSAGE, io)
        }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
    ): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        try {
            val keyBytes = SecretKeyFactory.getInstance(KEY_ALGORITHM).generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            // Clears the copy PBEKeySpec made. The caller's array is theirs to clear.
            spec.clearPassword()
        }
    }

    private const val WRONG_PASSPHRASE_MESSAGE =
        "Could not read this backup. The passphrase is wrong, or the file has been altered or " +
            "damaged since it was created."
}
