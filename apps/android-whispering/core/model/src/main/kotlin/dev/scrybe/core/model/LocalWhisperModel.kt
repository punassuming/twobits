package dev.scrybe.core.model

private const val SHERPA_ASR_BASE_URL =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"

enum class LocalWhisperModel(
    val displayName: String,
    val description: String,
    val archiveName: String,
    val dirName: String,
    val filePrefix: String,
    val sizeLabel: String,
) {
    TINY(
        displayName = "Whisper Tiny",
        description = "Fastest; good for most use cases",
        archiveName = "sherpa-onnx-whisper-tiny.tar.bz2",
        dirName = "sherpa-onnx-whisper-tiny",
        filePrefix = "tiny",
        sizeLabel = "~150 MB",
    ),
    BASE_EN(
        displayName = "Whisper Base (English)",
        description = "~30% fewer errors than Tiny; good balance",
        archiveName = "sherpa-onnx-whisper-base.en.tar.bz2",
        dirName = "sherpa-onnx-whisper-base.en",
        filePrefix = "base.en",
        sizeLabel = "~290 MB",
    ),
    SMALL_EN(
        displayName = "Whisper Small (English)",
        description = "Highest accuracy; requires ~1 GB free memory",
        archiveName = "sherpa-onnx-whisper-small.en.tar.bz2",
        dirName = "sherpa-onnx-whisper-small.en",
        filePrefix = "small.en",
        sizeLabel = "~967 MB",
    ),
    ;

    val downloadUrl: String get() = "$SHERPA_ASR_BASE_URL$archiveName"

    companion object {
        val default: LocalWhisperModel = TINY

        fun fromName(name: String): LocalWhisperModel = entries.firstOrNull { it.name == name } ?: default
    }
}
