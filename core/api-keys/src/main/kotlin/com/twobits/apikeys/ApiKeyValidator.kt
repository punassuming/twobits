package com.twobits.apikeys

object ApiKeyValidator {
    private const val MIN_LENGTH = 20
    private const val PREFIX = "sk-"

    fun isValid(key: String): Boolean {
        val trimmed = key.trim()
        return trimmed.startsWith(PREFIX) && trimmed.length >= MIN_LENGTH
    }
}
