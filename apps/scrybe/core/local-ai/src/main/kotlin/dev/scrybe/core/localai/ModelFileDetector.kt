package dev.scrybe.core.localai

import com.twobits.core.localmodels.LocalLlmModel
import dev.scrybe.core.model.LocalWhisperModel

sealed class DetectedImportTarget {
    data class Whisper(
        val model: LocalWhisperModel,
    ) : DetectedImportTarget()

    data class Llm(
        val model: LocalLlmModel,
    ) : DetectedImportTarget()
}

/**
 * Identifies which local model a SAF-picked file corresponds to, from its display name alone —
 * lets a single top-level Import action figure out the target instead of requiring the user to
 * have pre-selected a model row before picking a file.
 */
object ModelFileDetector {
    /**
     * Exact match against each model's expected on-disk filename first — all filenames across
     * both enums are distinct. Falls back to a normalized substring match on the filename stem
     * (name minus extension) for a user-renamed file (e.g. "tiny (1).tar.bz2"). Deliberately no
     * looser matching (a bare "tiny" or "gemma" substring) — a miss should fail into manual
     * selection rather than risk silently importing as the wrong model.
     */
    fun detect(displayName: String): DetectedImportTarget? {
        LocalWhisperModel.entries
            .firstOrNull { it.archiveName == displayName }
            ?.let { return DetectedImportTarget.Whisper(it) }
        LocalLlmModel.entries
            .firstOrNull { it.fileName == displayName }
            ?.let { return DetectedImportTarget.Llm(it) }

        val normalized = displayName.lowercase()
        LocalWhisperModel.entries
            .firstOrNull { normalized.contains(it.archiveName.lowercase().substringBeforeLast('.')) }
            ?.let { return DetectedImportTarget.Whisper(it) }
        LocalLlmModel.entries
            .firstOrNull { normalized.contains(it.fileName.lowercase().substringBeforeLast('.')) }
            ?.let { return DetectedImportTarget.Llm(it) }

        return null
    }
}
