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

    val IDEA_BRAINSTORM =
        TransformProfile(
            id = "default-idea-brainstorm",
            name = "Brainstorm List",
            description = "Organises raw ideas into a structured brainstorm list.",
            systemPrompt = "You are a creative thinking coach. From the transcript below extract and organise all ideas into a bulleted brainstorm list, grouping related ideas under short headings where appropriate. Return only the list.\n\nTranscript:\n{{transcript}}",
            steps =
                listOf(
                    "You are a creative thinking coach. From the transcript below extract and organise all ideas into a bulleted brainstorm list, grouping related ideas under short headings where appropriate. Return only the list.\n\nTranscript:\n{{transcript}}",
                ),
            providerType = ProviderType.OPENAI,
            isDefault = false,
            mode = RecordingMode.IDEA,
        )

    val TASKS_LIST =
        TransformProfile(
            id = "default-tasks-list",
            name = "Task List",
            description = "Extracts actionable tasks from the transcript.",
            systemPrompt = "You are a productivity assistant. From the transcript below extract every actionable task or to-do item as a checkbox list (- [ ] task). Include owner names and deadlines where mentioned. Return only the task list.\n\nTranscript:\n{{transcript}}",
            steps =
                listOf(
                    "You are a productivity assistant. From the transcript below extract every actionable task or to-do item as a checkbox list (- [ ] task). Include owner names and deadlines where mentioned. Return only the task list.\n\nTranscript:\n{{transcript}}",
                ),
            providerType = ProviderType.OPENAI,
            isDefault = false,
            mode = RecordingMode.TASKS,
        )

    val CONVERSATION_SUMMARY =
        TransformProfile(
            id = "default-conversation-summary",
            name = "Conversation Summary",
            description = "Summarises a multi-party dialogue with key points per speaker.",
            systemPrompt = "You are a conversation analyst. From the transcript below produce a concise summary that captures: the main topic, each participant's key points, and any agreements or next steps. Use markdown headings.\n\nTranscript:\n{{transcript}}",
            steps =
                listOf(
                    "You are a conversation analyst. From the transcript below produce a concise summary that captures: the main topic, each participant's key points, and any agreements or next steps. Use markdown headings.\n\nTranscript:\n{{transcript}}",
                ),
            providerType = ProviderType.OPENAI,
            isDefault = false,
            mode = RecordingMode.CONVERSATION,
        )

    val STORY_NARRATIVE =
        TransformProfile(
            id = "default-story-narrative",
            name = "Narrative Write-up",
            description = "Transforms spoken story fragments into a polished narrative.",
            systemPrompt = "You are a skilled writer and editor. From the transcript below craft a polished, engaging narrative in prose form. Preserve the speaker's voice, fix grammatical issues, and organise the story with a clear beginning, middle, and end. Return only the narrative.\n\nTranscript:\n{{transcript}}",
            steps =
                listOf(
                    "You are a skilled writer and editor. From the transcript below craft a polished, engaging narrative in prose form. Preserve the speaker's voice, fix grammatical issues, and organise the story with a clear beginning, middle, and end. Return only the narrative.\n\nTranscript:\n{{transcript}}",
                ),
            providerType = ProviderType.OPENAI,
            isDefault = false,
            mode = RecordingMode.STORY,
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

    val ALL = listOf(
        CLEANUP_DICTATION, SUMMARIZE, ACTION_ITEMS, TRANSLATE,
        MEETING_SUMMARY, IDEA_BRAINSTORM, TASKS_LIST, CONVERSATION_SUMMARY, STORY_NARRATIVE, INTERVIEW_HIGHLIGHTS,
    )
}
