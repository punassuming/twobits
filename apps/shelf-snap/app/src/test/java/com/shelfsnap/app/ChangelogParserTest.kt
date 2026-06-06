package com.shelfsnap.app

import com.twobits.common.ReleaseNotesParser
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
}
