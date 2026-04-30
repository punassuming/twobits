package dev.scrybe.core.common

fun sanitizeFileName(title: String): String {
    val sanitized =
        title
            .replace(Regex("[/\\\\:*?\"<>|\\p{Cntrl}]"), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-', ' ')
    return sanitized.take(100).ifEmpty { "recording" }
}
