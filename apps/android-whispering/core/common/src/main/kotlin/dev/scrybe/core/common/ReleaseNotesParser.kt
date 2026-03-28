package dev.scrybe.core.common

data class ReleaseNotes(
    val title: String,
    val summaryItems: List<String>,
    val fullSection: String,
    val groups: List<ReleaseNotesGroup> = emptyList(),
)

data class ReleaseNotesGroup(
    val title: String,
    val items: List<String>,
)

object ReleaseNotesParser {
    fun parseLatestReleaseNotes(changelog: String): ReleaseNotes? {
        return parseReleaseHistory(changelog).firstOrNull()
    }

    fun parseReleaseHistory(changelog: String): List<ReleaseNotes> {
        val lines = changelog.lines()
        val sectionIndices =
            lines.mapIndexedNotNull { index, line ->
                if (line.startsWith("## ")) index else null
            }
        if (sectionIndices.isEmpty()) return emptyList()

        return sectionIndices.mapIndexedNotNull { idx, startIndex ->
            val endIndex = sectionIndices.getOrNull(idx + 1) ?: lines.size
            val sectionLines = lines.subList(startIndex, endIndex)
            val title = normalizeHeading(sectionLines.first().removePrefix("## ").trim())
            if (title.equals(UNRELEASED_TITLE, ignoreCase = true)) {
                null
            } else {
                val bullets =
                    sectionLines.asSequence()
                        .map { it.trim() }
                        .filter { it.startsWith("* ") || it.startsWith("- ") }
                        .map { normalizeBullet(it) }
                        .filter { it.isNotBlank() }
                        .toList()
                ReleaseNotes(
                    title = title,
                    summaryItems = bullets.take(MAX_SUMMARY_ITEMS),
                    fullSection = sectionLines.joinToString("\n").trim(),
                    groups = groupBulletsByArea(bullets),
                )
            }
        }
    }

    private fun normalizeHeading(text: String): String = text.replace(LINK_REGEX, "$1")

    private fun normalizeBullet(text: String): String =
        text.removePrefix("* ")
            .removePrefix("- ")
            .replace(LINK_REGEX, "$1")
            .replace(COMMIT_LINK_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

    private fun groupBulletsByArea(bullets: List<String>): List<ReleaseNotesGroup> {
        val grouped =
            linkedMapOf(
                "Recording" to mutableListOf<String>(),
                "AI & Processing" to mutableListOf<String>(),
                "UI & Workflow" to mutableListOf<String>(),
                "Platform & Reliability" to mutableListOf<String>(),
                "Other" to mutableListOf<String>(),
            )

        bullets.forEach { bullet ->
            grouped[categorizeBullet(bullet)]?.add(bullet)
        }

        return grouped.entries
            .filter { it.value.isNotEmpty() }
            .map { (title, items) -> ReleaseNotesGroup(title = title, items = items) }
    }

    private fun categorizeBullet(bullet: String): String {
        val normalized = bullet.lowercase()
        return when {
            normalized.contains("record") || normalized.contains("audio") || normalized.contains("microphone") ->
                "Recording"
            normalized.contains("openai") || normalized.contains("transcrib") || normalized.contains("transform") ||
                normalized.contains("prompt") || normalized.contains("api key") || normalized.contains("ai") ->
                "AI & Processing"
            normalized.contains("screen") || normalized.contains("icon") || normalized.contains("button") ||
                normalized.contains("history") || normalized.contains("profile") || normalized.contains("settings") ||
                normalized.contains("release note") || normalized.contains("popup") ->
                "UI & Workflow"
            normalized.contains("build") || normalized.contains("service") || normalized.contains("version") ||
                normalized.contains("windows") || normalized.contains("sdk") || normalized.contains("fix") ->
                "Platform & Reliability"
            else -> "Other"
        }
    }

    private const val MAX_SUMMARY_ITEMS = 6
    private const val UNRELEASED_TITLE = "Unreleased"
    private val LINK_REGEX = Regex("\\[(.+?)]\\(.+?\\)")
    private val COMMIT_LINK_REGEX = Regex("\\s*\\([^)]+\\)$")
    private val MULTI_SPACE_REGEX = Regex("\\s+")
}
