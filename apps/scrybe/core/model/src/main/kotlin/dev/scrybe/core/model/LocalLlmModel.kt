package dev.scrybe.core.model

import com.twobits.core.localmodels.LocalModelAcquisition
import com.twobits.core.localmodels.LocalModelFamily
import com.twobits.core.localmodels.LocalModelSpec
import com.twobits.core.localmodels.LocalModelTask

/**
 * Google's own `litert-community` org on HuggingFace hosts pre-converted `.litertlm` bundles for
 * Gemma 4, published for direct app consumption (unlike Google's `google/gemma-*` repos, which
 * sit behind a license click-through), so this app can do a plain unauthenticated GET the same
 * way it already does for Whisper's archives. Apache-2.0 licensed.
 *
 * VERIFICATION STATUS: `litert-community/gemma-4-E4B-it-litert-lm` and its exact file name
 * (`gemma-4-E4B-it.litertlm`, 3.66 GB, Apache-2.0) were confirmed against a live browse of the
 * repo's file tree — it's a single generic bundle, not hardware-specific (the repo also has
 * separate `-web.litertlm`/`-web.task` files for the web/JS runtime, which don't apply here).
 * `litert-community/gemma-4-E2B-it-litert-lm` is confirmed to be a real, current repo (same live
 * browse of the org page), but [GEMMA_4_E2B.fileName]/[sizeLabel] follow the same
 * `{repo-name}.litertlm` pattern just confirmed for E4B rather than an independently verified
 * file listing — verify before relying on this in production; the sandbox this was written in
 * blocks huggingface.co entirely. [sha256] is intentionally null until someone can compute it
 * against a verified download; fill in both at the same time.
 */
enum class LocalLlmModel(
    override val displayName: String,
    override val description: String,
    val fileName: String,
    override val sizeLabel: String,
    val downloadUrl: String,
    val huggingFacePageUrl: String,
    val sha256: String? = null,
) : LocalModelSpec {
    GEMMA_4_E2B(
        displayName = "Gemma 4 E2B",
        description = "Fastest on-device inference",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeLabel = "~2.2 GB",
        downloadUrl =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/" +
                "gemma-4-E2B-it.litertlm",
        huggingFacePageUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
    ),
    GEMMA_4_E4B(
        displayName = "Gemma 4 E4B",
        description = "Better quality · requires 4 GB+ RAM",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeLabel = "~3.7 GB",
        downloadUrl =
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/" +
                "gemma-4-E4B-it.litertlm",
        huggingFacePageUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
    ),
    ;

    override val id: String get() = name
    override val family: LocalModelFamily get() = LocalModelFamily.GEMMA
    override val task: LocalModelTask get() = LocalModelTask.TEXT
    override val acquisition: LocalModelAcquisition
        get() = LocalModelAcquisition.DownloadFile(fileName, downloadUrl, huggingFacePageUrl, sha256)

    companion object {
        val default: LocalLlmModel = GEMMA_4_E2B

        fun fromName(name: String): LocalLlmModel = entries.firstOrNull { it.name == name } ?: default
    }
}
