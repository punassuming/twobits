package com.shelfsnap.app.data.model

enum class LocalMoondreamModel(
    val displayName: String,
    val description: String,
    val fileName: String,
    val sizeLabel: String,
    val huggingFacePageUrl: String,
) {
    MOONDREAM2(
        displayName = "Moondream 2",
        description = "Lightweight vision model; good for category and condition detection",
        fileName = "moondream2.gguf",
        sizeLabel = "~1.7 GB",
        huggingFacePageUrl = "huggingface.co/vikhyatk/moondream2",
    ),
    ;

    companion object {
        val default: LocalMoondreamModel = MOONDREAM2

        fun fromName(name: String): LocalMoondreamModel =
            entries.firstOrNull { it.name == name } ?: default
    }
}
