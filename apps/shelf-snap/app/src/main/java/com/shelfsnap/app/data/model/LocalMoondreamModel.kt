package com.shelfsnap.app.data.model

import com.twobits.core.localmodels.LocalModelAcquisition
import com.twobits.core.localmodels.LocalModelFamily
import com.twobits.core.localmodels.LocalModelSpec
import com.twobits.core.localmodels.LocalModelTask

enum class LocalMoondreamModel(
    override val displayName: String,
    override val description: String,
    val fileName: String,
    override val sizeLabel: String,
    val huggingFacePageUrl: String,
) : LocalModelSpec {
    MOONDREAM2(
        displayName = "Moondream 2",
        description = "Lightweight vision model; good for category and condition detection",
        fileName = "moondream2.gguf",
        sizeLabel = "~1.7 GB",
        huggingFacePageUrl = "huggingface.co/vikhyatk/moondream2",
    ),
    ;

    override val id: String get() = name
    override val family: LocalModelFamily get() = LocalModelFamily.MOONDREAM
    override val task: LocalModelTask get() = LocalModelTask.VISION
    override val acquisition: LocalModelAcquisition
        get() = LocalModelAcquisition.ImportFile(fileName, huggingFacePageUrl)

    companion object {
        val default: LocalMoondreamModel = MOONDREAM2

        fun fromName(name: String): LocalMoondreamModel = entries.firstOrNull { it.name == name } ?: default
    }
}
