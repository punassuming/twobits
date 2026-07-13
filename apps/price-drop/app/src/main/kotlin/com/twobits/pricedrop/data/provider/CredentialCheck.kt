package com.twobits.pricedrop.data.provider

import com.twobits.apikeys.ApiKeyValidator

/** Outcome of a lightweight, offline credential format check. */
data class CredentialCheckResult(
    val isValid: Boolean,
    val message: String,
)

/**
 * Pure, offline validation of a BYOK key for a given provider. Kept free of Android/coroutine
 * dependencies so it is directly unit-testable. OpenAI keys are checked against the shared
 * [ApiKeyValidator] (`sk-` prefix, min length); other providers accept any reasonably-sized key
 * since their formats vary (Jina, SearchAPI.io, Serper, Rainforest, LinkMyDeals).
 */
object CredentialCheck {
    private const val MIN_GENERIC_LENGTH = 8

    fun check(
        provider: PriceDropProvider,
        key: String,
    ): CredentialCheckResult {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            return CredentialCheckResult(false, "Enter an API key")
        }
        return if (provider == PriceDropProvider.OPENAI) {
            if (ApiKeyValidator.isValid(trimmed)) {
                CredentialCheckResult(true, "Key looks valid")
            } else {
                CredentialCheckResult(false, "OpenAI keys start with sk- and are at least 20 characters")
            }
        } else {
            if (trimmed.length >= MIN_GENERIC_LENGTH) {
                CredentialCheckResult(true, "Key looks valid")
            } else {
                CredentialCheckResult(false, "That key looks too short")
            }
        }
    }
}
