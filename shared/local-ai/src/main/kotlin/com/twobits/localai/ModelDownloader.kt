package com.twobits.localai

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Stateless single-file download + integrity check shared by each app's own (stateful,
 * DataStore-backed) `LocalModelManager`. Deliberately has no state of its own — model-family
 * tracking, [com.twobits.core.localmodels.LocalModelState] flows, and preference persistence
 * stay app-owned since each app tracks a different set of model families.
 *
 * Downloads to a `.part` sibling file and only renames it to [dest] once the transfer completes
 * and (if [expectedSha256] is supplied) its checksum verifies — [dest] itself never exists in a
 * partial/truncated state, so a caller's existence check (`dest.exists() && dest.length() > 0`)
 * can never be fooled by an interrupted download into reporting a broken file as installed.
 *
 * Resumes automatically: a `.part` file left over from an earlier attempt (network drop, stalled
 * connection, app killed mid-transfer) is continued via an HTTP Range request instead of
 * restarted from byte 0, and transient failures retry with backoff instead of surfacing
 * immediately — up to [MAX_ATTEMPTS] times, with the `.part` file preserved between attempts so
 * each retry picks up from wherever the last one stopped. These models are multi-gigabyte; on a
 * flaky connection the previous restart-from-scratch behavior could burn many times the model's
 * own size in repeated partial transfers before ever completing, or filling the device's storage
 * before it ever did.
 */
object ModelDownloader {
    /**
     * @param expectedSha256 when non-null, the completed download is verified against this
     *   lowercase-hex SHA-256 before being installed to [dest]; a mismatch discards the partial
     *   file rather than retrying (retrying bad bytes won't produce good ones) and throws.
     */
    fun downloadFile(
        okHttpClient: OkHttpClient,
        url: String,
        dest: File,
        expectedSha256: String? = null,
        onProgress: (Int) -> Unit,
    ) {
        val client =
            okHttpClient
                .newBuilder()
                // No cap on the transfer as a whole — these are multi-gigabyte files and a
                // healthy-but-slow connection can legitimately take many minutes. connectTimeout
                // and readTimeout below are what actually detect a dead connection: a genuine
                // stall (no bytes for 30s) throws instead of hanging indefinitely, which
                // previously left the only way out being to force-kill the app mid-write.
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        val part = File(dest.parentFile, "${dest.name}$PART_SUFFIX")

        var attempt = 0
        var lastError: IOException? = null
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            try {
                attemptDownload(client, url, part, onProgress)
                verifyAndInstall(part, dest, expectedSha256)
                return
            } catch (e: NonRetryableDownloadException) {
                if (e.discardPartial) part.delete()
                throw e
            } catch (e: IOException) {
                lastError = e
                if (attempt >= MAX_ATTEMPTS) break
                Thread.sleep(backoffMs(attempt))
            }
        }
        // .part is intentionally left in place on exhausted retries — a later call (the user
        // tapping Retry, or an automatic retry next launch) resumes from here instead of
        // re-downloading everything already received.
        throw lastError ?: IOException("Download failed after $MAX_ATTEMPTS attempts")
    }

    private fun attemptDownload(
        client: OkHttpClient,
        url: String,
        part: File,
        onProgress: (Int) -> Unit,
    ) {
        val startOffset = if (part.exists()) part.length() else 0L
        val requestBuilder = Request.Builder().url(url)
        if (startOffset > 0) requestBuilder.addHeader("Range", "bytes=$startOffset-")

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 416) {
                // "Range Not Satisfiable" usually means the .part file is stale against a
                // since-changed remote copy — but it's also exactly what a legitimately
                // *complete* transfer produces if the app died after the last byte arrived but
                // before verifyAndInstall ran (e.g. killed mid-hash on a multi-gigabyte file):
                // the next Range request starts past the server's own length. Distinguish the
                // two via the response's authoritative total (RFC 7233 "bytes */total"); only
                // discard when the part file doesn't actually match it.
                val remoteTotal = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                if (remoteTotal != null && remoteTotal == part.length()) {
                    return
                }
                part.delete()
                throw IOException("Range not satisfiable (HTTP 416); discarding partial file")
            }
            // 408/429 are transient (timeout / rate limit) and worth retrying with backoff;
            // every other 4xx (bad URL, auth, gone) will fail identically forever.
            if (response.code in 400..499 && response.code != 408 && response.code != 429) {
                throw NonRetryableDownloadException("HTTP ${response.code}")
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

            val body = response.body ?: throw IOException("Empty response body")
            val resumed = startOffset > 0 && response.code == 206
            if (startOffset > 0 && !resumed) {
                // We asked for a range but the server sent the whole file back (no Range
                // support on this host) — the .part file's bytes aren't a valid prefix of this
                // response, so restart this attempt rather than corrupt the file by appending a
                // full copy on top of what's already there.
                part.delete()
            }
            val effectiveStart = if (resumed) startOffset else 0L
            val total = totalSize(response, body.contentLength(), effectiveStart)
            if (total > 0) checkDiskSpace(part, total - effectiveStart)

            body.byteStream().use { input ->
                FileOutputStream(part, resumed).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead = effectiveStart
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (total > 0) onProgress(((bytesRead * 100) / total).toInt())
                    }
                }
            }
        }
    }

    /**
     * Prefers the authoritative total from `Content-Range: bytes start-end/total`; falls back to
     * this response's own Content-Length added to [offset] when Content-Range is absent (a 200
     * response has no range to report, so its Content-Length already is the full size at
     * offset 0). Returns -1 when neither is available (e.g. chunked transfer with no length) —
     * callers skip the disk-space check and percent progress in that case, same as before.
     */
    private fun totalSize(
        response: Response,
        contentLength: Long,
        offset: Long,
    ): Long {
        val parsedTotal = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
        return parsedTotal ?: if (contentLength > 0) offset + contentLength else -1L
    }

    private fun checkDiskSpace(
        part: File,
        remainingBytes: Long,
    ) {
        val dir = part.parentFile ?: return
        val usable = dir.usableSpace
        val required = (remainingBytes * 1.05).toLong() // 5% margin for filesystem overhead
        if (usable < required) {
            // Keep the partial file — it's already-downloaded progress, and the fix here is
            // freeing up *other* space, not losing this download's place in line too.
            throw NonRetryableDownloadException(
                "Not enough storage: need ~${required.toHumanBytes()} free, have ${usable.toHumanBytes()}",
                discardPartial = false,
            )
        }
    }

    private fun verifyAndInstall(
        part: File,
        dest: File,
        expectedSha256: String?,
    ) {
        if (expectedSha256 != null && !matchesSha256(part, expectedSha256)) {
            throw NonRetryableDownloadException("Downloaded file didn't match the expected checksum")
        }
        if (!part.renameTo(dest)) {
            throw IOException("Couldn't finalize download (rename failed)")
        }
    }

    private fun backoffMs(attempt: Int): Long = (INITIAL_BACKOFF_MS * (1L shl (attempt - 1))).coerceAtMost(MAX_BACKOFF_MS)

    private fun Long.toHumanBytes(): String {
        val gb = this / 1_073_741_824.0
        return if (gb >= 1) "%.1f GB".format(gb) else "%.0f MB".format(this / 1_048_576.0)
    }

    /** Case-insensitive comparison against a lowercase-hex SHA-256 of [file]'s contents. */
    fun matchesSha256(
        file: File,
        expectedHex: String,
    ): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
        return actualHex.equals(expectedHex, ignoreCase = true)
    }

    /** Removes an in-progress download's `.part` file for [dest], if any — e.g. when the caller abandons this download in favor of a different model, or deletes an installed one. */
    fun deletePartialFile(dest: File) {
        File(dest.parentFile, "${dest.name}$PART_SUFFIX").delete()
    }

    /**
     * Deletes any `.part` file under [dir] whose last write is older than [maxAgeMs] — an
     * attempt nobody has resumed in a long time is more likely abandoned than still wanted, and
     * unlike a Ready model file this is disk usage the UI never shows the user, so it can't
     * otherwise be noticed and cleaned up by hand.
     */
    fun cleanupStalePartialFiles(
        dir: File,
        maxAgeMs: Long = STALE_PART_MAX_AGE_MS,
    ) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        dir.listFiles { f -> f.isFile && f.name.endsWith(PART_SUFFIX) }
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    /**
     * A failure that retrying won't fix — the caller should stop immediately rather than burn
     * through [MAX_ATTEMPTS] backoff delays first. [discardPartial] is false only for
     * insufficient-storage: that partial is legitimate progress toward a download that can still
     * complete once space is freed, unlike a bad checksum or a URL that will 404 forever.
     */
    private class NonRetryableDownloadException(
        message: String,
        val discardPartial: Boolean = true,
    ) : IOException(message)

    private const val PART_SUFFIX = ".part"
    private const val BUFFER_SIZE = 8192
    private const val MAX_ATTEMPTS = 5
    private const val INITIAL_BACKOFF_MS = 2_000L
    private const val MAX_BACKOFF_MS = 30_000L
    private const val STALE_PART_MAX_AGE_MS = 3 * 24 * 60 * 60 * 1000L // 3 days
}
