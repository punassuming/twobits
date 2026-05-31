package dev.scrybe.core.model

enum class LocalGemmaModel(
    val displayName: String,
    val description: String,
    val fileName: String,
    val sizeLabel: String,
    val downloadUrl: String,
) {
    GEMMA_2_2B_GPU(
        displayName = "Gemma 2 2B IT · GPU",
        description = "Fast inference; requires GPU (Pixel 6+, Snapdragon 888+)",
        fileName = "gemma2-2b-it-gpu-int8.task",
        sizeLabel = "~2.6 GB",
        downloadUrl =
            "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/" +
                "gemma2-2b-it-gpu-int8.task",
    ),
    GEMMA_2_2B_CPU(
        displayName = "Gemma 2 2B IT · CPU",
        description = "Runs on any Android device; slower inference",
        fileName = "gemma2-2b-it-cpu-int8.task",
        sizeLabel = "~2.3 GB",
        downloadUrl =
            "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/" +
                "gemma2-2b-it-cpu-int8.task",
    ),
    ;

    companion object {
        val default: LocalGemmaModel = GEMMA_2_2B_GPU

        fun fromName(name: String): LocalGemmaModel = entries.firstOrNull { it.name == name } ?: default
    }
}
