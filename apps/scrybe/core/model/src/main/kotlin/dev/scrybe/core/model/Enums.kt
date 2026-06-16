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

enum class AudioFormat {
    AAC,
    MP3,
    MP4,
    OGG,
    WAV,
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

enum class RecordingMode {
    MEETING,
    IDEA,
    TASKS,
    CONVERSATION,
    STORY,
    INTERVIEW,
    JOURNAL,
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
            }
}
