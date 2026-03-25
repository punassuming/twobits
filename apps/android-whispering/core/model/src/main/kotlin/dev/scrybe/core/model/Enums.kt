package dev.scrybe.core.model

enum class SessionStatus {
    IDLE,
    RECORDING,
    STOPPING,
    RECORDED,
    QUEUED,
    TRANSCRIBING,
    TRANSCRIBED,
    EDITED,
    ARCHIVED,
    FAILED,
}

enum class TranscriptType {
    RAW,
    EDITED,
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

enum class PostStopDestination {
    HOME,
    SESSION_REVIEW,
}
