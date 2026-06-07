package dev.scrybe.core.model

enum class OpenAiTranscriptionModel(
    val apiName: String,
    val title: String,
    val subtitle: String,
    val costLabel: String,
) {
    WHISPER_1(
        apiName = "whisper-1",
        title = "Whisper 1",
        subtitle = "Standard · recommended",
        costLabel = "$0.006/min",
    ),
    WHISPER_LARGE(
        apiName = "whisper-large-v3",
        title = "Whisper Large",
        subtitle = "Higher accuracy",
        costLabel = "$0.011/min",
    ),
    ;

    companion object {
        val default: OpenAiTranscriptionModel = WHISPER_1

        fun fromApiName(name: String): OpenAiTranscriptionModel =
            entries.firstOrNull { it.apiName == name } ?: default
    }
}
