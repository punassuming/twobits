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
 * VERIFIED: both URLs, file names, and [sha256] checksums below were confirmed against real
 * downloads (this sandbox can't reach huggingface.co, so that verification happened outside
 * it) — not the usual sourced-from-search-results caveat elsewhere in this codebase's local
 * model entries. `litertlm-android`'s Gradle resolution and Scrybe's minSdk compatibility were
 * confirmed the same way.
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
        sizeLabel = "~2.4 GB",
        downloadUrl =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/" +
                "gemma-4-E2B-it.litertlm",
        huggingFacePageUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
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
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
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
