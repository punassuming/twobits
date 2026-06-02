package com.shelfsnap.app

import com.shelfsnap.app.ui.whatsnew.parseChangelog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogParserTest {

    private val sample = """
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
    fun `parses sections by their headings and strips brackets`() {
        val sections = parseChangelog(sample)
        assertEquals(listOf("Unreleased", "1.0.0 - 2026-06-01"), sections.map { it.title })
    }

    @Test
    fun `ignores preamble before the first heading`() {
        val sections = parseChangelog(sample)
        assertTrue(sections.none { it.title.contains("Changelog") })
        // The "Added" subsection and bullet land in the section body.
        assertTrue(sections.first().body.any { it.startsWith("- ") })
    }

    @Test
    fun `empty input yields no sections`() {
        assertTrue(parseChangelog("").isEmpty())
    }
}
