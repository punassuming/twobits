package com.twobits.core.localmodels

/**
 * Google's own `litert-community` org on HuggingFace hosts pre-converted `.litertlm` bundles for
 * Gemma 4, published for direct app consumption (unlike Google's `google/gemma-*` repos, which
 * sit behind a license click-through), so each app can do a plain unauthenticated GET the same
 * way Scrybe's Whisper archives already work. Apache-2.0 licensed.
 *
 * Shared across Scrybe, Shelf Snap, and PriceDrop — unlike [LocalWhisperModel] (Scrybe-only) or
 * Shelf Snap's own vision model, all three apps want the literal same on-device text model, so
 * this is the deliberate exception to keeping model inventories app-owned (see
 * [TwoBitsLocalModels]'s doc comment): one spec avoids three copies of the same URLs/hashes
 * drifting out of sync.
 *
 * VERIFIED — all four entries' URLs and file names were confirmed against real downloads (this
 * sandbox can't reach huggingface.co, so verification happened outside it). GEMMA_4_E2B/E4B's
 * [sha256] checksums were confirmed the same way; GEMMA_3N_E2B/E4B's URLs are confirmed reachable
 * with a plain unauthenticated GET (despite living under `google/gemma-3n-*-it-litert-lm`, not
 * `litert-community`) but their checksums are not yet captured, so [sha256] is left null there —
 * see [LocalModelAcquisition.DownloadFile.sha256]'s doc for what that means for integrity
 * checking. `litertlm-android`'s Gradle resolution and Scrybe's minSdk compatibility were
 * confirmed the same way as the checksums.
 *
 * Gemma 3n exists alongside Gemma 4 (not replacing it) for the widespread Gemma 4 + LiteRT-LM
 * stability reports independent of vision — see PR #147's review discussion.
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
    GEMMA_3N_E2B(
        displayName = "Gemma 3n E2B",
        description = "Older, more stable runtime — try this if Gemma 4 crashes",
        fileName = "gemma-3n-E2B-it-int4.litertlm",
        sizeLabel = "~2.5 GB",
        downloadUrl =
            "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/" +
                "gemma-3n-E2B-it-int4.litertlm",
        huggingFacePageUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm",
        sha256 = null,
    ),
    GEMMA_3N_E4B(
        displayName = "Gemma 3n E4B",
        description = "Older, more stable runtime, better quality — requires 4 GB+ RAM",
        fileName = "gemma-3n-E4B-it-int4.litertlm",
        sizeLabel = "~4 GB",
        downloadUrl =
            "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/main/" +
                "gemma-3n-E4B-it-int4.litertlm",
        huggingFacePageUrl = "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm",
        sha256 = null,
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
