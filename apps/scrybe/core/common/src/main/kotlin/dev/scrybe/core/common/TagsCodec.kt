package dev.scrybe.core.common

object TagsCodec {
    fun encode(tags: List<String>): String =
        tags
            .map(::normalizeTag)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")

    // Splits on the same separators as [normalizeInput]: commas and semicolons are never part of
    // a tag (every input path treats them as separators), so stored values that slipped through
    // un-split — e.g. "research,roadmap" saved as one tag — heal into separate tags on load.
    fun decode(value: String): List<String> = normalizeInput(value)

    fun normalizeInput(value: String): List<String> =
        value
            .split(',', '\n', ';')
            .map(::normalizeTag)
            .filter(String::isNotBlank)
            .distinct()
            .toList()

    private fun normalizeTag(value: String): String =
        value
            .trim()
            .replace(Regex("\\s+"), " ")
}
