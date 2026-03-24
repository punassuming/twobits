package dev.scrybe.core.model

enum class SessionStatus {
    IDLE,
    RECORDING,
    STOPPING,
    RECORDED,
    TRANSCRIBING,
    TRANSCRIBED,
    FAILED,
}

enum class TranscriptType {
    RAW,
    TRANSFORMED,
}

enum class TransformStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
}

enum class ProviderType {
    OPENAI,
    LOCAL,
}

enum class AudioFormat {
    AAC,
    MP4,
    OGG,
    WEBM,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
