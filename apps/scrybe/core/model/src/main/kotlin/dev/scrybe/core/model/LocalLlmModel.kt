package dev.scrybe.core.model

import com.twobits.core.localmodels.LocalModelAcquisition
import com.twobits.core.localmodels.LocalModelFamily
import com.twobits.core.localmodels.LocalModelSpec
import com.twobits.core.localmodels.LocalModelTask

/**
 * Google's own `litert-community` org on HuggingFace hosts pre-converted `.litertlm` bundles for
 * Qwen3, published for direct app consumption (unlike Google's `google/gemma-*` repos, which sit
 * behind a license click-through), so this app can do a plain unauthenticated GET the same way it
 * already does for Whisper's archives.
 *
 * IMPORTANT — verification status: these specific URLs and file names were sourced from
 * web-search results (including a real `litert-lm run --from-huggingface-repo=...` usage
 * example for the 4B file), not independently fetched and confirmed — the sandbox this was
 * written in blocks huggingface.co entirely. Verify both resolve to a real, ungated .litertlm
 * file — not a 404, not a login/consent page — before relying on this in production. [sha256]
 * is intentionally null until someone can compute it against a verified download; fill it in at
 * the same time.
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
    QWEN3_0_6B(
        displayName = "Qwen3 0.6B",
        description = "Fastest on-device inference",
        fileName = "Qwen3-0.6B.litertlm",
        sizeLabel = "~600 MB",
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        huggingFacePageUrl = "https://huggingface.co/litert-community/Qwen3-0.6B",
    ),
    QWEN3_4B(
        displayName = "Qwen3 4B",
        description = "Better quality · requires 4 GB+ RAM",
        fileName = "qwen3_4b_instruct_2507_mixed_int4.litertlm",
        sizeLabel = "~2.5 GB",
        downloadUrl =
            "https://huggingface.co/litert-community/Qwen3-4B-Instruct-2507/resolve/main/" +
                "qwen3_4b_instruct_2507_mixed_int4.litertlm",
        huggingFacePageUrl = "https://huggingface.co/litert-community/Qwen3-4B-Instruct-2507",
    ),
    ;

    override val id: String get() = name
    override val family: LocalModelFamily get() = LocalModelFamily.GEMMA
    override val task: LocalModelTask get() = LocalModelTask.TEXT
    override val acquisition: LocalModelAcquisition
        get() = LocalModelAcquisition.DownloadFile(fileName, downloadUrl, huggingFacePageUrl, sha256)

    companion object {
        val default: LocalLlmModel = QWEN3_0_6B

        fun fromName(name: String): LocalLlmModel = entries.firstOrNull { it.name == name } ?: default
    }
}
