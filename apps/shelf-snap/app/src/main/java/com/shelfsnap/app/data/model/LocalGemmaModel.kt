package com.shelfsnap.app.data.model

import com.twobits.core.localmodels.LocalModelAcquisition
import com.twobits.core.localmodels.LocalModelFamily
import com.twobits.core.localmodels.LocalModelSpec
import com.twobits.core.localmodels.LocalModelTask

enum class LocalGemmaModel(
    override val displayName: String,
    override val description: String,
    val fileName: String,
    override val sizeLabel: String,
    val huggingFacePageUrl: String,
) : LocalModelSpec {
    GEMMA_3_1B(
        displayName = "Gemma 3 1B",
        description = "Fastest on-device descriptions and price estimates",
        fileName = "gemma-3-1b-it-q4_0.gguf",
        sizeLabel = "~800 MB",
        huggingFacePageUrl = "huggingface.co/google/gemma-3-1b-it-qat-q4_0-GGUF",
    ),
    GEMMA_3_4B(
        displayName = "Gemma 3 4B",
        description = "Better listing quality · requires 4 GB+ RAM",
        fileName = "gemma-3-4b-it-q4_0.gguf",
        sizeLabel = "~2.6 GB",
        huggingFacePageUrl = "huggingface.co/google/gemma-3-4b-it-qat-q4_0-GGUF",
    ),
    ;

    override val id: String get() = name
    override val family: LocalModelFamily get() = LocalModelFamily.GEMMA
    override val task: LocalModelTask get() = LocalModelTask.TEXT
    override val acquisition: LocalModelAcquisition
        get() = LocalModelAcquisition.ImportFile(fileName, huggingFacePageUrl)

    companion object {
        val default: LocalGemmaModel = GEMMA_3_1B

        fun fromName(name: String): LocalGemmaModel = entries.firstOrNull { it.name == name } ?: default
    }
}
