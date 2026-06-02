package com.twobits.network

object HttpErrorMapper {
    fun map(code: Int, message: String = ""): String =
        when (code) {
            401, 403 -> "API key is invalid or has expired. Please check your key in Settings."
            429 -> "Too many requests. Please wait a moment and try again."
            in 500..599 -> "Server error ($code). Please try again later."
            else -> "Request failed: $code ${message.take(100)}".trim()
        }
}
