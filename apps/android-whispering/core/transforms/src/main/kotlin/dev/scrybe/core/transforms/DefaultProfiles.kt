package dev.scrybe.core.transforms

import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.model.TransformProfile

object DefaultProfiles {
    val CLEANUP_DICTATION = TransformProfile(
        id = "default-cleanup",
        name = "Cleanup Dictation",
        description = "Cleans up filler words, punctuation, and formatting from dictated text.",
        systemPrompt = "You are a helpful editor. Clean up the following dictated text by fixing punctuation, removing filler words, and improving readability. Return only the cleaned text.",
        providerType = ProviderType.OPENAI,
        isDefault = true,
    )

    val SUMMARIZE = TransformProfile(
        id = "default-summarize",
        name = "Summarize",
        description = "Produces a concise summary of the transcript.",
        systemPrompt = "You are a helpful assistant. Summarize the following text concisely. Return only the summary.",
        providerType = ProviderType.OPENAI,
        isDefault = false,
    )

    val ACTION_ITEMS = TransformProfile(
        id = "default-action-items",
        name = "Action Items",
        description = "Extracts action items from the transcript.",
        systemPrompt = "You are a helpful assistant. Extract all action items from the following text as a bulleted list. Return only the action items.",
        providerType = ProviderType.OPENAI,
        isDefault = false,
    )

    val ALL = listOf(CLEANUP_DICTATION, SUMMARIZE, ACTION_ITEMS)
}
