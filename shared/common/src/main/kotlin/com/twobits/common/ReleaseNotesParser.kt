package com.twobits.common

data class ReleaseNoteItem(
    val title: String,
    val description: String,
)

data class ReleaseNotes(
    val title: String,
    val date: String = "",
    val summaryItems: List<String>,
    val fullSection: String,
    val groups: List<ReleaseNotesGroup> = emptyList(),
)

data class ReleaseNotesGroup(
    val title: String,
    val items: List<ReleaseNoteItem>,
)

object ReleaseNotesParser {
    fun parseLatestReleaseNotes(changelog: String): ReleaseNotes? =
        parseReleaseHistory(changelog).firstOrNull()

    fun parseReleaseHistory(changelog: String): List<ReleaseNotes> {
        val lines = changelog.lines()
        val sectionIndices = lines.mapIndexedNotNull { index, line ->
            if (line.startsWith("## ")) index else null
        }
        if (sectionIndices.isEmpty()) return emptyList()

        return sectionIndices.mapIndexedNotNull { idx, startIndex ->
            val endIndex = sectionIndices.getOrNull(idx + 1) ?: lines.size
            val sectionLines = lines.subList(startIndex, endIndex)
            val headingRaw = sectionLines.first().removePrefix("## ").trim()
            val title = normalizeHeading(headingRaw)
            if (title.equals(UNRELEASED_TITLE, ignoreCase = true)) {
                null
            } else {
                val date = DATE_REGEX.find(headingRaw)?.value.orEmpty()
                val groups = parseStructuredGroups(sectionLines)
                val summaryItems = if (groups.isNotEmpty()) {
                    groups.flatMap { g -> g.items.map { it.title } }.take(MAX_SUMMARY_ITEMS)
                } else {
                    sectionLines.asSequence()
                        .map { it.trim() }
                        .filter { it.startsWith("* ") || it.startsWith("- ") }
                        .map { normalizeBullet(it) }
                        .filter { it.isNotBlank() }
                        .take(MAX_SUMMARY_ITEMS)
                        .toList()
                }
                ReleaseNotes(
                    title = title,
                    date = date,
                    summaryItems = summaryItems,
                    fullSection = sectionLines.joinToString("\n").trim(),
                    groups = groups,
                )
            }
        }
    }

    /**
     * Parses a version block's lines into structured groups.
     *
     * Expected format:
     * ```
     * ### Features
     * **Topic** — headline:
     * * detail one
     * * detail two
     * **Other topic** — description
     * ```
     * Falls back to empty list if no `### ` sub-headings are found.
     */
    private fun parseStructuredGroups(sectionLines: List<String>): List<ReleaseNotesGroup> {
        val subHeadings = sectionLines.mapIndexedNotNull { i, line ->
            if (line.startsWith("### ")) i else null
        }
        if (subHeadings.isEmpty()) return emptyList()

        val groups = mutableListOf<ReleaseNotesGroup>()
        subHeadings.forEachIndexed { si, subStart ->
            val subEnd = subHeadings.getOrNull(si + 1) ?: sectionLines.size
            val groupLines = sectionLines.subList(subStart, subEnd)
            val rawLabel = groupLines.first().removePrefix("### ").trim()
            val groupLabel = when (rawLabel.lowercase()) {
                "features" -> "Features & Enhancements"
                "improvements" -> "Improvements"
                "fixes", "bug fixes" -> "Bug Fixes"
                else -> rawLabel
            }
            val items = parseGroupItems(groupLines.drop(1))
            if (items.isNotEmpty()) {
                groups += ReleaseNotesGroup(title = groupLabel, items = items)
            }
        }
        return groups
    }

    /**
     * Parses lines within a `### ` section into `ReleaseNoteItem` objects.
     * A `**bold**` line starts a new item; following `* ` bullets form its description.
     */
    private fun parseGroupItems(lines: List<String>): List<ReleaseNoteItem> {
        val items = mutableListOf<ReleaseNoteItem>()
        var currentTitle: String? = null
        val currentBullets = mutableListOf<String>()

        fun flush() {
            val t = currentTitle ?: return
            items += ReleaseNoteItem(
                title = t,
                description = currentBullets.joinToString(" · ").ifBlank { t },
            )
            currentTitle = null
            currentBullets.clear()
        }

        for (raw in lines) {
            val line = raw.trim()
            when {
                line.startsWith("**") -> {
                    flush()
                    // Strip markdown bold and trailing colon: "**Camera** — headline:" → "Camera — headline"
                    currentTitle = line.replace(BOLD_REGEX, "$1").trimEnd(':').trim()
                }
                (line.startsWith("* ") || line.startsWith("- ")) -> {
                    if (currentTitle != null) {
                        currentBullets += normalizeBullet(line)
                    } else {
                        val text = normalizeBullet(line)
                        items += ReleaseNoteItem(title = text, description = text)
                    }
                }
            }
        }
        flush()
        return items
    }

    private fun normalizeHeading(text: String): String =
        text.replace(LINK_REGEX, "$1")
            .replace(SQUARE_BRACKET_REGEX, "$1")
            .replace(DATE_REGEX, "")
            .replace(PAREN_REGEX, "")
            .replace(TRAILING_SEPARATOR_REGEX, "")
            .trim()

    private fun normalizeBullet(text: String): String =
        text.removePrefix("* ")
            .removePrefix("- ")
            .replace(LINK_REGEX, "$1")
            .replace(COMMIT_LINK_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

    private const val MAX_SUMMARY_ITEMS = 6
    private const val UNRELEASED_TITLE = "Unreleased"
    private val LINK_REGEX = Regex("\\[(.+?)]\\(.+?\\)")
    private val COMMIT_LINK_REGEX = Regex("\\s*\\([^)]+\\)$")
    private val MULTI_SPACE_REGEX = Regex("\\s+")
    private val DATE_REGEX = Regex("\\(\\d{4}-\\d{2}-\\d{2}\\)|\\d{4}-\\d{2}-\\d{2}")
    private val PAREN_REGEX = Regex("\\s*\\(.*?\\)")
    private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
    private val SQUARE_BRACKET_REGEX = Regex("\\[(.+?)]")
    private val TRAILING_SEPARATOR_REGEX = Regex("\\s*-\\s*$")
}
