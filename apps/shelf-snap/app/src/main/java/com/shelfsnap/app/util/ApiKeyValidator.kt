package com.shelfsnap.app.util

/**
 * Validates OpenAI API keys before they are saved or used for a network call.
 *
 * Kept as a pure object (no Android dependencies) so it can be unit tested and
 * reused by both the Settings screen and the analysis pre-check.
 */
object ApiKeyValidator {

    /** OpenAI keys are well over this length; this only guards against obvious junk. */
    private const val MIN_LENGTH = 20

    /** OpenAI keys begin with this prefix (e.g. `sk-...`, `sk-proj-...`). */
    private const val PREFIX = "sk-"

    fun isValid(key: String): Boolean {
        val trimmed = key.trim()
        return trimmed.startsWith(PREFIX) && trimmed.length >= MIN_LENGTH
    }
}
