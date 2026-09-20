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

/**
 * A recording format the user can choose.
 *
 * [actualExtension] and [actualMimeType] describe **what the encoder really produces**, which is not
 * always what the name suggests. `MediaRecorder` gives us MPEG-4/AAC for [AAC], [MP3], [MP4] and
 * [WAV] alike — it has no MP3 encoder and no PCM/WAV muxer — so those four all produce an MP4
 * container with AAC audio regardless of which one is picked.
 *
 * Recordings already on disk are therefore named `.mp3` or `.wav` while containing MPEG-4. Nothing
 * inside Scrybe cared, because it decodes by content. Anything *outside* Scrybe does care: a player
 * trusting a `.wav` extension looks for RIFF headers and fails. So exports name files by these
 * properties rather than by the stored extension, and [MP3] and [WAV] are no longer offered for new
 * recordings.
 *
 * The constants stay, and must: `recording_sessions.audioFormat` stores the enum *name*, so
 * deleting [WAV] would turn every existing WAV-labelled session into a `valueOf` crash on read.
 */
enum class AudioFormat(
    val description: String,
    val actualExtension: String,
    val actualMimeType: String,
    /** False for formats the encoder cannot actually produce; hidden from the recording picker. */
    val isSelectable: Boolean = true,
) {
    AAC("Best overall · small files, good quality, universal support", "m4a", "audio/mp4"),
    MP3("Universal compatibility · widely supported, slightly larger than AAC", "m4a", "audio/mp4", isSelectable = false),
    MP4("Container with AAC audio · good for video-audio workflows", "m4a", "audio/mp4"),
    OGG("Open format · excellent quality/size ratio on Android", "ogg", "audio/ogg"),
    WAV("Lossless · maximum quality, largest file size", "m4a", "audio/mp4", isSelectable = false),
    WEBM("Web-optimized · good for streaming or web sharing", "webm", "audio/webm"),
    ;

    companion object {
        /** The formats offered for new recordings — the ones whose names are truthful. */
        val selectable: List<AudioFormat> get() = entries.filter { it.isSelectable }
    }
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
