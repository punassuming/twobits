package dev.scrybe.core.common

object TagsCodec {
    fun encode(tags: List<String>): String =
        tags
            .map(::normalizeTag)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString("\n")

    fun decode(value: String): List<String> =
        value
            .lineSequence()
            .map(::normalizeTag)
            .filter(String::isNotBlank)
            .distinct()
            .toList()

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
