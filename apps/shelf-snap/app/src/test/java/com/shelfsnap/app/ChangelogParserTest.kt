package com.shelfsnap.app

import com.twobits.common.ReleaseNotesParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogParserTest {
    private val sample =
        """
        # Changelog

        Preamble text that should be ignored.

        ## [Unreleased]

        ### Added
        - A brand new thing

        ## [1.0.0] - 2026-06-01

        ### Added
        - First release
        """.trimIndent()

    @Test
    fun `parses versioned sections and excludes Unreleased`() {
        val sections = ReleaseNotesParser.parseReleaseHistory(sample)
        assertEquals(listOf("1.0.0"), sections.map { it.title })
    }

    @Test
    fun `ignores preamble before the first heading`() {
        val sections = ReleaseNotesParser.parseReleaseHistory(sample)
        assertTrue(sections.none { it.title.contains("Changelog") })
        assertTrue(sections.first().summaryItems.isNotEmpty())
    }

    @Test
    fun `empty input yields no sections`() {
        assertTrue(ReleaseNotesParser.parseReleaseHistory("").isEmpty())
    }

    @Test
    fun `strips inline backticks and bold markers from bullets`() {
        val changelog =
            """
            ## 2.0.0 (2026-06-08)

            ### Fixes
            - fix `gradlew` task and **bold** text in notes
            """.trimIndent()
        val section = ReleaseNotesParser.parseReleaseHistory(changelog).single()
        assertEquals(
            listOf("fix gradlew task and bold text in notes"),
            section.summaryItems,
        )
    }

    @Test
    fun `strips backticks from bold item titles`() {
        val changelog =
            """
            ## 2.1.0 (2026-06-08)

            ### Improvements

            **`Settings`** — cleaner `layout`:
            * row spacing normalised
            """.trimIndent()
        val section = ReleaseNotesParser.parseReleaseHistory(changelog).single()
        val item =
            section.groups
                .single()
                .items
                .single()
        assertEquals("Settings", item.title)
        assertEquals("cleaner layout · row spacing normalised", item.description)
    }
}
