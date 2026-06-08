package dev.scrybe.core.model

enum class LocalGemmaModel(
    val displayName: String,
    val description: String,
    val fileName: String,
    val sizeLabel: String,
    val huggingFacePageUrl: String,
) {
    GEMMA_3_1B(
        displayName = "Gemma 3 1B",
        description = "Fastest on-device inference",
        fileName = "gemma-3-1b-it-q4_0.gguf",
        sizeLabel = "~800 MB",
        huggingFacePageUrl = "huggingface.co/google/gemma-3-1b-it-qat-q4_0-GGUF",
    ),
    GEMMA_3_4B(
        displayName = "Gemma 3 4B",
        description = "Better quality · requires 4 GB+ RAM",
        fileName = "gemma-3-4b-it-q4_0.gguf",
        sizeLabel = "~2.6 GB",
        huggingFacePageUrl = "huggingface.co/google/gemma-3-4b-it-qat-q4_0-GGUF",
    ),
    ;

    companion object {
        val default: LocalGemmaModel = GEMMA_3_1B

        fun fromName(name: String): LocalGemmaModel = entries.firstOrNull { it.name == name } ?: default
    }
}
