package com.twobits.localai

import android.app.ActivityManager
import android.content.Context
import java.io.File

/** One reading of [ActivityManager.getMemoryInfo], in bytes. */
data class MemorySnapshot(
    val availBytes: Long,
    val totalBytes: Long,
    /** Below this much available memory the OS starts killing background processes. */
    val thresholdBytes: Long,
    val lowMemory: Boolean,
) {
    val availMb: Long get() = availBytes / BYTES_PER_MB
    val totalMb: Long get() = totalBytes / BYTES_PER_MB

    /** Short form for log lines, e.g. `mem=1432/5891 MB`. */
    fun summary(): String = "mem=$availMb/$totalMb MB"

    companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}

/** Thrown by [LocalInferenceMemoryGuard.requireHeadroom] — carries the actual numbers in [message]. */
class InsufficientMemoryException(
    message: String,
) : RuntimeException(message)

/**
 * Refuses to start a native model load the device can't fit. Without this, the only outcome of
 * loading a multi-GB LiteRT-LM model on a device that's short on RAM is the low-memory killer
 * SIGKILLing the process partway through — no Kotlin exception, no tombstone, nothing in the
 * app's own logs beyond a dangling "-start" entry. A refusal here is an ordinary exception every
 * caller's existing `runCatching` already turns into an in-app error and a Debug Log entry, with
 * the real numbers attached.
 *
 * The headroom factors are deliberately rough: LiteRT-LM memory-maps the weights, so the
 * resident footprint is roughly the file size plus KV cache/activations for text, and the image
 * encoder plus its soft tokens on top for vision. They're meant to catch the hopeless case
 * (a 2.4 GB model with 1 GB free), not to be a precise accounting.
 */
object LocalInferenceMemoryGuard {
    private const val TEXT_HEADROOM_FACTOR = 1.1
    private const val VISION_HEADROOM_FACTOR = 1.3

    fun snapshot(context: Context): MemorySnapshot? {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return null
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return MemorySnapshot(
            availBytes = info.availMem,
            totalBytes = info.totalMem,
            thresholdBytes = info.threshold,
            lowMemory = info.lowMemory,
        )
    }

    fun requiredBytes(
        modelFile: File,
        vision: Boolean,
    ): Long {
        val factor = if (vision) VISION_HEADROOM_FACTOR else TEXT_HEADROOM_FACTOR
        return (modelFile.length() * factor).toLong()
    }

    /**
     * Throws [InsufficientMemoryException] when the device is already in its low-memory state or
     * the memory left above the OS's kill threshold is less than [requiredBytes]. A device whose
     * memory can't be read at all (no [ActivityManager]) is let through rather than blocked.
     */
    fun requireHeadroom(
        context: Context,
        modelFile: File,
        vision: Boolean,
    ) {
        val snapshot = snapshot(context) ?: return
        val required = requiredBytes(modelFile, vision)
        val usable = snapshot.availBytes - snapshot.thresholdBytes
        if (!snapshot.lowMemory && usable >= required) return
        val modelName = modelFile.nameWithoutExtension
        val requiredMb = required / MemorySnapshot.BYTES_PER_MB
        val state = if (snapshot.lowMemory) "the device is already low on memory" else "only ${snapshot.availMb} MB is available"
        throw InsufficientMemoryException(
            "Not enough free memory to run $modelName (needs about $requiredMb MB free; $state, " +
                "of ${snapshot.totalMb} MB total). Close other apps or pick a smaller model.",
        )
    }
}
