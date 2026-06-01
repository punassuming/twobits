package dev.scrybe.core.transforms

import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.RecordingMode
import dev.scrybe.core.model.TransformProfile

object DefaultProfiles {
    val CLEANUP_DICTATION =
        TransformProfile(
            id = "default-cleanup",
            name = "Cleanup Dictation",
            description = "Cleans up filler words, punctuation, and formatting from dictated text.",
            systemPrompt = "You are a helpful editor. Clean up the dictated text below by fixing punctuation, removing filler words, and improving readability. Return only the cleaned text.\n\nDictation:\n{{transcript}}",
            steps =
                listOf(
                    "You are a helpful editor. Clean up the dictated text below by fixing punctuation, removing filler words, and improving readability. Return only the cleaned text.\n\nDictation:\n{{transcript}}",
                ),
            providerType = ProviderType.OPENAI,
            isDefault = true,
        )

    val SUMMARIZE =
        TransformProfile(
            id = "default-summarize",
            name = "Summarize",
            description = "Produces a concise summary of the transcript.",
            systemPrompt = "You are a helpful assistant. Summarize the transcript below concisely. Return only the summary.\n\nTranscript:\n{{transcript}}",
            steps =
                listOf(
                    "You are a helpful assistant. Summarize the transcript below concisely. Return only the summary.\n\nTranscript:\n{{transcript}}",
                ),
            providerType = ProviderType.OPENAI,
            isDefault = false,
        )

    val ACTION_ITEMS =
        TransformProfile(
            id = "default-action-items",
            name = "Action Items",
            description = "Extracts action items from the transcript.",
            systemPrompt = "You are a helpful assistant. Extract all action items from the transcript below as a bulleted list. Return only the action items.\n\nTranscript:\n{{transcript}}",
            steps =
                listOf(
                    "You are a helpful assistant. Extract all action items from the transcript below as a bulleted list. Return only the action items.\n\nTranscript:\n{{transcript}}",
                ),
            providerType = ProviderType.OPENAI,
            isDefault = false,
        )

    val TRANSLATE =
        TransformProfile(
            id = "default-translate",
            name = "Translate to English",
            description = "Translates the transcript into English, preserving speaker labels.",
            systemPrompt = "You are a professional translator. Translate the following transcript to English. If speaker labels are present (e.g. \"SPEAKER_00:\"), preserve them unchanged. Return only the translated text.\n\nTranscript:\n{{transcript}}",
            steps =
                listOf(
                    "You are a professional translator. Translate the following transcript to English. If speaker labels are present (e.g. \"SPEAKER_00:\"), preserve them unchanged. Return only the translated text.\n\nTranscript:\n{{transcript}}",
                ),
            providerType = ProviderType.OPENAI,
            isDefault = false,
        )

    val MEETING_SUMMARY =
        TransformProfile(
            id = "default-meeting-summary",
            name = "Meeting Summary",
            description = "Produces a structured summary with decisions and action items.",
            systemPrompt = "You are an expert meeting facilitator. From the transcript below produce: a brief summary (2-3 sentences), a bullet list of key decisions, and a bullet list of action items with owner names where mentioned. Use markdown headings.\n\nTranscript:\n{{transcript}}",
            steps =
                listOf(
                    "You are an expert meeting facilitator. From the transcript below produce: a brief summary (2-3 sentences), a bullet list of key decisions, and a bullet list of action items with owner names where mentioned. Use markdown headings.\n\nTranscript:\n{{transcript}}",
                ),
            providerType = ProviderType.OPENAI,
            isDefault = false,
            mode = RecordingMode.MEETING,
        )

    val INTERVIEW_HIGHLIGHTS =
        TransformProfile(
            id = "default-interview-highlights",
            name = "Interview Highlights",
            description = "Extracts key questions, answers, and notable quotes.",
            systemPrompt = "You are an interviewer's assistant. From the transcript below produce: a list of the most important questions asked, a concise answer summary for each, and 2-3 notable direct quotes. Use markdown headings.\n\nTranscript:\n{{transcript}}",
            steps =
                listOf(
                    "You are an interviewer's assistant. From the transcript below produce: a list of the most important questions asked, a concise answer summary for each, and 2-3 notable direct quotes. Use markdown headings.\n\nTranscript:\n{{transcript}}",
                ),
            providerType = ProviderType.OPENAI,
            isDefault = false,
            mode = RecordingMode.INTERVIEW,
        )

    val ALL = listOf(CLEANUP_DICTATION, SUMMARIZE, ACTION_ITEMS, TRANSLATE, MEETING_SUMMARY, INTERVIEW_HIGHLIGHTS)
}
