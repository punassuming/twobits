package com.twobits.localai

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Stateless single-file download + integrity check shared by each app's own (stateful,
 * DataStore-backed) `LocalModelManager`. Deliberately has no state of its own — model-family
 * tracking, [com.twobits.core.localmodels.LocalModelState] flows, and preference persistence
 * stay app-owned since each app tracks a different set of model families.
 */
object ModelDownloader {
    fun downloadFile(
        okHttpClient: OkHttpClient,
        url: String,
        dest: File,
        onProgress: (Int) -> Unit,
    ) {
        val client =
            okHttpClient
                .newBuilder()
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val body = response.body ?: throw IOException("Empty response body")
        val contentLength = body.contentLength()
        body.byteStream().use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) onProgress(((bytesRead * 100) / contentLength).toInt())
                }
            }
        }
    }

    /** Case-insensitive comparison against a lowercase-hex SHA-256 of [file]'s contents. */
    fun matchesSha256(
        file: File,
        expectedHex: String,
    ): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
        return actualHex.equals(expectedHex, ignoreCase = true)
    }
}
