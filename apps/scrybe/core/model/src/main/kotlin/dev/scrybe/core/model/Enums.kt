package dev.scrybe.core.model

enum class SessionStatus {
    IDLE,
    RECORDING,
    STOPPING,
    RECORDED,
    QUEUED,
    TRANSCRIBING,
    TRANSCRIBED,
    PARTIAL_TRANSCRIPTION,
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

enum class AudioFormat(
    val description: String,
) {
    AAC("Best overall · small files, good quality, universal support"),
    MP3("Universal compatibility · widely supported, slightly larger than AAC"),
    MP4("Container with AAC audio · good for video-audio workflows"),
    OGG("Open format · excellent quality/size ratio on Android"),
    WAV("Lossless · maximum quality, largest file size"),
    WEBM("Web-optimized · good for streaming or web sharing"),
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

enum class RecordingMode {
    MEETING,
    IDEA,
    TASKS,
    CONVERSATION,
    STORY,
    INTERVIEW,
    JOURNAL,
    CUSTOM,
    ;

    val label: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }

    val outputDescription: String
        get() =
            when (this) {
                MEETING -> "Action items + summary"
                IDEA -> "Brainstorm list"
                TASKS -> "Task list"
                CONVERSATION -> "Dialogue summary"
                STORY -> "Narrative write-up"
                INTERVIEW -> "Q&A + highlights"
                JOURNAL -> "Plain transcript"
                CUSTOM -> "Custom recording type"
            }
}
