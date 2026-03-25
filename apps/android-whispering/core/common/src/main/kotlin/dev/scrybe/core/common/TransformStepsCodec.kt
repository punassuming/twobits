package dev.scrybe.core.common

object TransformStepsCodec {
    private const val STEP_DELIMITER = "\u001E"

    fun encode(steps: List<String>): String =
        steps
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(STEP_DELIMITER)

    fun decode(encoded: String, fallback: String = ""): List<String> {
        val decoded = encoded
            .split(STEP_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return when {
            decoded.isNotEmpty() -> decoded
            fallback.isNotBlank() -> listOf(fallback.trim())
            else -> emptyList()
        }
    }
}
