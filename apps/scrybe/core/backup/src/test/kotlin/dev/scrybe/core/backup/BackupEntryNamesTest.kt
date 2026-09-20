package dev.scrybe.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Restore reads an archive the user hands it, so entry names are hostile input until proven
 * otherwise. Each case below is a real way zip extraction gets exploited, and the guard is
 * allow-listing — so these also document what a legitimate entry looks like.
 */
class BackupEntryNamesTest {
    @Test
    fun `a well formed audio entry yields its session id`() {
        val id = "3f2b7c1a-9d4e-4a11-bd8c-2f0e91a7c4d5"
        assertEquals(id, BackupEntryNames.sessionIdOfAudioEntry(BackupEntryNames.audioEntry(id, "m4a")))
    }

    @Test
    fun `the entry name this build writes is the one it accepts`() {
        val name = BackupEntryNames.audioEntry("abc-123", "ogg")
        assertEquals("audio/abc-123.ogg", name)
        assertEquals("abc-123", BackupEntryNames.sessionIdOfAudioEntry(name))
    }

    @Test
    fun `a parent directory traversal is refused`() {
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audio/../../databases/scrybe-db"))
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("../audio/abc.m4a"))
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audio/..%2F..%2Fx.m4a"))
    }

    @Test
    fun `an absolute path is refused`() {
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("/data/data/dev.scrybe.android/files/x.m4a"))
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audio//etc/passwd"))
    }

    @Test
    fun `a windows separator is refused`() {
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audio\\..\\..\\x.m4a"))
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audio/sub\\dir.m4a"))
    }

    @Test
    fun `nesting under the audio prefix is refused`() {
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audio/nested/abc.m4a"))
    }

    @Test
    fun `an entry outside the audio prefix is not treated as audio`() {
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("manifest.json"))
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("database.json"))
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audios/abc.m4a"))
    }

    @Test
    fun `a missing or empty extension is refused`() {
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audio/abc"))
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audio/abc."))
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audio/.m4a"))
    }

    @Test
    fun `a session id outside the uuid alphabet is refused`() {
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audio/abc def.m4a"))
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audio/abc;rm.m4a"))
        assertNull(BackupEntryNames.sessionIdOfAudioEntry("audio/${"a".repeat(65)}.m4a"))
    }

    @Test
    fun `the metadata entries are recognised and nothing else is`() {
        assertTrue(BackupEntryNames.isMetadataEntry(BackupEntryNames.MANIFEST))
        assertTrue(BackupEntryNames.isMetadataEntry(BackupEntryNames.DATABASE))
        assertEquals(false, BackupEntryNames.isMetadataEntry("audio/abc.m4a"))
        assertEquals(false, BackupEntryNames.isMetadataEntry("../manifest.json"))
    }
}
