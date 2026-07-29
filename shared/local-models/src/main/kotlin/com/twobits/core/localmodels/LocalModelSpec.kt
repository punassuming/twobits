package com.twobits.core.localmodels

/** Which on-device model family a spec belongs to. */
enum class LocalModelFamily { WHISPER, GEMMA, MOONDREAM }

/** What the model is used for. Drives which AI-config section surfaces it. */
enum class LocalModelTask { TRANSCRIBE, TEXT, VISION }

/**
 * How a model's weights are obtained on-device. Archive-download and single-file-import are
 * mutually exclusive: a sealed type makes illegal combinations (e.g. an archive spec that also
 * carries a single file name) unrepresentable.
 */
sealed interface LocalModelAcquisition {
    /**
     * Downloaded archive that must be extracted on-device (Scrybe Whisper / sherpa-onnx). The
     * extracted directory contains `$filePrefix-encoder.int8.onnx`, `$filePrefix-decoder.int8.onnx`
     * and `$filePrefix-tokens.txt`.
     */
    data class DownloadArchive(
        val archiveName: String,
        val dirName: String,
        val filePrefix: String,
        val downloadUrl: String,
    ) : LocalModelAcquisition

    /** User-supplied single weights file imported via SAF (Gemma / Moondream `.gguf`). */
    data class ImportFile(
        val fileName: String,
        val huggingFacePageUrl: String,
    ) : LocalModelAcquisition

    /**
     * Downloaded single weights file needing no extraction — distinct from [DownloadArchive]
     * (multi-file archive) and [ImportFile] (no network step at all; the user supplies an
     * already-downloaded file via SAF). [downloadUrl] must be reachable with a plain
     * unauthenticated GET — a HuggingFace repo gated behind license click-through won't work
     * here and needs [ImportFile] instead, since this app never handles HuggingFace auth.
     */
    data class DownloadFile(
        val fileName: String,
        val downloadUrl: String,
        val huggingFacePageUrl: String,
        /**
         * Expected SHA-256 of the downloaded bytes, lowercase hex, or null when no verified
         * hash is available yet. When present, the downloader checks it after the transfer and
         * treats a mismatch as a failed acquisition (deletes the file, surfaces
         * [LocalModelState.Error]) instead of silently trusting whatever a third-party mirror
         * served — [downloadUrl] points at a community re-upload, not the model author's own
         * repo, so this is the integrity check that substitutes for that provenance.
         */
        val sha256: String? = null,
    ) : LocalModelAcquisition
}

/**
 * Shared, app-agnostic description of an installable on-device model. App enums
 * (`LocalWhisperModel`, `LocalGemmaModel`, `LocalMoondreamModel`) implement this so the UI and
 * managers can treat any model uniformly. The set of concrete models (the inventory) stays
 * app-owned — this interface only fixes the contract.
 */
interface LocalModelSpec {
    /** Stable identifier (the enum constant name); used for persistence keys. */
    val id: String
    val displayName: String
    val description: String
    val sizeLabel: String
    val family: LocalModelFamily
    val task: LocalModelTask
    val acquisition: LocalModelAcquisition
}
