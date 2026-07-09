package dev.scrybe.core.transcription

data class TranscriptionOptions(
    val language: String? = null,
    val prompt: String? = null,
    val responseFormat: String = "json",
    val model: String = "whisper-1",
)

/**
 * Instruction sent with gpt-4o-family transcription calls (batch and realtime) so mixed-language
 * recordings keep each utterance in its spoken language — without it these models tend to
 * normalize everything into the recording's dominant language. Deliberately NOT sent to
 * whisper-1: its `prompt` is a decoder text prefix, not an instruction channel, and an
 * English-language prefix can itself bias a non-English recording.
 */
const val PRESERVE_LANGUAGES_PROMPT =
    "Transcribe each utterance exactly as spoken, in its original language. " +
        "If multiple languages are spoken, keep every utterance in the language it was spoken in. Never translate."
