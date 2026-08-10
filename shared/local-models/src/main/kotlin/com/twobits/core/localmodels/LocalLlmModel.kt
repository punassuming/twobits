package com.twobits.core.localmodels

/**
 * `litert-community` on HuggingFace hosts pre-converted `.litertlm` bundles published for direct
 * app consumption (unlike Google's own `google/gemma-*` repos, which sit behind a license
 * click-through and 401 on a plain unauthenticated GET — see the GEMMA_3N history below), so each
 * app can download these the same way Scrybe's Whisper archives already work. Apache-2.0 licensed.
 *
 * Shared across Scrybe, Shelf Snap, and PriceDrop — unlike [LocalWhisperModel] (Scrybe-only) or
 * Shelf Snap's own vision model, all three apps want the literal same on-device text models, so
 * this is the deliberate exception to keeping model inventories app-owned (see
 * [TwoBitsLocalModels]'s doc comment): one spec avoids three copies of the same URLs/hashes
 * drifting out of sync.
 *
 * VERIFIED (GEMMA_4_E2B / GEMMA_4_E4B only): those two entries' URLs, file names, and [sha256]
 * checksums were confirmed against real downloads (this sandbox can't reach huggingface.co, so
 * that verification happened outside it). GEMMA_3_1B, GEMMA_3_270M, and QWEN_3_1_7B's URLs came
 * directly from the user as working "generic" (non-chip-specific) `litert-community` artifacts;
 * their [sha256] is left null since it hasn't been captured yet. `litertlm-android`'s Gradle
 * resolution and Scrybe's minSdk compatibility were confirmed the same way as GEMMA_4's checksums.
 *
 * [visionCapable] gates which entries [Shelf Snap's vision picker][LocalModelTask.VISION] offers:
 * only GEMMA_4_E2B/E4B have ever been used for on-device vision (`generateWithImage()`) and even
 * that usage is EXPERIMENTAL/unverified per Shelf Snap's own AI config screen copy — none of the
 * newer, smaller, or non-Gemma entries have any vision verification at all, so they default to
 * `false` rather than risk silently offering a model that can't actually take an image input.
 *
 * A Gemma 3n fallback (for the widespread Gemma 4 + LiteRT-LM stability reports — see PR #147's
 * review) was attempted twice and reverted twice — see PR #148/#149's history. Both `.../resolve/
 * main/gemma-3n-E2B-it-int4.litertlm` and the plain (no `-int4`) filename under `google/gemma-3n-
 * *-it-litert-lm` returned HTTP 401 on a real device: the repo's browsable page and file listing
 * are public, but `google/gemma-3n-*` sits behind a HuggingFace license click-through that gates
 * the actual file bytes — a plain unauthenticated GET, the only kind
 * [LocalModelAcquisition.DownloadFile] supports, can't get past that regardless of filename.
 * `litert-community` has no equivalent Gemma 3n `.litertlm` repo. Re-add only once either (a) an
 * unauthenticated mirror exists, verified against a real download — not just a reachable-looking
 * page — or (b) [LocalModelAcquisition.ImportFile] (defined, but not yet wired into any app's
 * download UI) is built out so this can route through manual SAF import instead of a direct
 * download.
 */
enum class LocalLlmModel(
    override val displayName: String,
    override val description: String,
    val fileName: String,
    override val sizeLabel: String,
    val downloadUrl: String,
    val huggingFacePageUrl: String,
    override val family: LocalModelFamily,
    val visionCapable: Boolean = false,
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
        family = LocalModelFamily.GEMMA,
        visionCapable = true,
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
        family = LocalModelFamily.GEMMA,
        visionCapable = true,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
    ),
    GEMMA_3_1B(
        displayName = "Gemma 3 1B",
        description = "Small and fast — good default for lower-RAM devices",
        fileName = "gemma3-1b-it-int4.litertlm",
        sizeLabel = "~584 MB",
        downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm",
        huggingFacePageUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
        family = LocalModelFamily.GEMMA,
    ),
    GEMMA_3_270M(
        displayName = "Gemma 3 270M",
        description = "Smallest and fastest — lowest quality, for constrained devices",
        fileName = "gemma3-270m-it-q8.litertlm",
        sizeLabel = "~304 MB",
        downloadUrl = "https://huggingface.co/litert-community/gemma-3-270m-it/resolve/main/gemma3-270m-it-q8.litertlm",
        huggingFacePageUrl = "https://huggingface.co/litert-community/gemma-3-270m-it",
        family = LocalModelFamily.GEMMA,
    ),
    QWEN_3_1_7B(
        displayName = "Qwen 3 1.7B",
        description = "Different model family — an alternative if Gemma doesn't work well on your device",
        fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
        sizeLabel = "~977 MB",
        downloadUrl =
            "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/main/" +
                "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
        huggingFacePageUrl = "https://huggingface.co/litert-community/Qwen3-1.7B",
        family = LocalModelFamily.QWEN,
    ),
    ;

    override val id: String get() = name
    override val task: LocalModelTask get() = LocalModelTask.TEXT
    override val acquisition: LocalModelAcquisition
        get() = LocalModelAcquisition.DownloadFile(fileName, downloadUrl, huggingFacePageUrl, sha256)

    companion object {
        val default: LocalLlmModel = GEMMA_4_E2B

        fun fromName(name: String): LocalLlmModel = entries.firstOrNull { it.name == name } ?: default
    }
}
