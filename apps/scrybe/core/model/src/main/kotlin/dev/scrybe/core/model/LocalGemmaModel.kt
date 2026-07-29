package dev.scrybe.core.model

import com.twobits.core.localmodels.LocalModelAcquisition
import com.twobits.core.localmodels.LocalModelFamily
import com.twobits.core.localmodels.LocalModelSpec
import com.twobits.core.localmodels.LocalModelTask

/**
 * Google's own official Gemma GGUF repos on HuggingFace (under `google/`) require accepting a
 * license while logged in — an unauthenticated download fails — which is why this previously
 * only supported manual browser-download + SAF import. [downloadUrl] instead points at an
 * ungated community GGUF re-upload of the same public weights, so this app can do a plain
 * unauthenticated GET the same way it already does for Whisper's archives.
 *
 * IMPORTANT — verification status: these specific URLs were sourced from web-search results,
 * not independently fetched and confirmed (the sandbox this was written in blocks
 * huggingface.co entirely). Verify both resolve to a real, ungated .gguf file — not a 404, not
 * a login/consent page — before relying on this in production. [sha256] is intentionally null
 * until someone can compute it against a verified download; fill it in at the same time.
 */
enum class LocalGemmaModel(
    override val displayName: String,
    override val description: String,
    val fileName: String,
    override val sizeLabel: String,
    val downloadUrl: String,
    val huggingFacePageUrl: String,
    val sha256: String? = null,
) : LocalModelSpec {
    GEMMA_3_1B(
        displayName = "Gemma 3 1B",
        description = "Fastest on-device inference",
        fileName = "gemma-3-1b-it-Q4_K_M.gguf",
        sizeLabel = "~800 MB",
        downloadUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf",
        huggingFacePageUrl = "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF",
    ),
    GEMMA_3_4B(
        displayName = "Gemma 3 4B",
        description = "Better quality · requires 4 GB+ RAM",
        fileName = "gemma-3-4b-it-Q4_K_M.gguf",
        sizeLabel = "~2.6 GB",
        downloadUrl = "https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
        huggingFacePageUrl = "https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF",
    ),
    ;

    override val id: String get() = name
    override val family: LocalModelFamily get() = LocalModelFamily.GEMMA
    override val task: LocalModelTask get() = LocalModelTask.TEXT
    override val acquisition: LocalModelAcquisition
        get() = LocalModelAcquisition.DownloadFile(fileName, downloadUrl, huggingFacePageUrl, sha256)

    companion object {
        val default: LocalGemmaModel = GEMMA_3_1B

        fun fromName(name: String): LocalGemmaModel = entries.firstOrNull { it.name == name } ?: default
    }
}
