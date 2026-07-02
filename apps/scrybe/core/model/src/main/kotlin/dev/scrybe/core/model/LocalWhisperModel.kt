package dev.scrybe.core.model

import com.twobits.core.localmodels.LocalModelAcquisition
import com.twobits.core.localmodels.LocalModelFamily
import com.twobits.core.localmodels.LocalModelSpec
import com.twobits.core.localmodels.LocalModelTask

private const val SHERPA_ASR_BASE_URL =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"

enum class LocalWhisperModel(
    override val displayName: String,
    override val description: String,
    val archiveName: String,
    val dirName: String,
    val filePrefix: String,
    override val sizeLabel: String,
) : LocalModelSpec {
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
    MEDIUM(
        displayName = "Whisper Medium",
        description = "Best local accuracy · multilingual",
        archiveName = "sherpa-onnx-whisper-medium.tar.bz2",
        dirName = "sherpa-onnx-whisper-medium",
        filePrefix = "medium",
        sizeLabel = "~1.5 GB",
    ),
    ;

    val downloadUrl: String get() = "$SHERPA_ASR_BASE_URL$archiveName"

    override val id: String get() = name
    override val family: LocalModelFamily get() = LocalModelFamily.WHISPER
    override val task: LocalModelTask get() = LocalModelTask.TRANSCRIBE
    override val acquisition: LocalModelAcquisition
        get() = LocalModelAcquisition.DownloadArchive(archiveName, dirName, filePrefix, downloadUrl)

    companion object {
        val default: LocalWhisperModel = TINY

        fun fromName(name: String): LocalWhisperModel = entries.firstOrNull { it.name == name } ?: default
    }
}
