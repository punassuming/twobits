package com.twobits.securestore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "twobits_cred_key"
private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
private const val IV_LENGTH = 12
private const val GCM_TAG_LENGTH = 128

/**
 * AES-256/GCM encryption backed by AndroidKeyStore.
 *
 * encrypt() → Base64(IV || ciphertext+tag)
 * decrypt() → plaintext
 * tryDecryptOrPassthrough() → handles legacy plaintext gracefully (re-encrypt on next write)
 */
@Singleton
class CredentialCrypto
    @Inject
    constructor() {
        private fun getOrCreateKey(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
            keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
            val keyGen = KeyGenerator.getInstance(ALGORITHM, KEYSTORE_PROVIDER)
            keyGen.init(
                KeyGenParameterSpec
                    .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            return keyGen.generateKey()
        }

        fun encrypt(plaintext: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val blob = ByteArray(IV_LENGTH + ciphertext.size)
            System.arraycopy(iv, 0, blob, 0, IV_LENGTH)
            System.arraycopy(ciphertext, 0, blob, IV_LENGTH, ciphertext.size)
            return Base64.encodeToString(blob, Base64.NO_WRAP)
        }

        fun decrypt(encoded: String): String {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = blob.copyOfRange(0, IV_LENGTH)
            val ciphertext = blob.copyOfRange(IV_LENGTH, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }

        /**
         * Returns decrypted value, or the raw stored string if it looks like legacy plaintext.
         * On next write the caller should call encrypt() so the value migrates silently.
         */
        fun tryDecryptOrPassthrough(stored: String): String {
            if (stored.isBlank()) return stored
            return try {
                val blob = Base64.decode(stored, Base64.NO_WRAP)
                if (blob.size <= IV_LENGTH) return stored
                decrypt(stored)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                stored
            }
        }
    }
