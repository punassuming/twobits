package dev.scrybe.core.backup

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A backup is read by a build that did not write it — that is the entire point of one. So the
 * decoder has to survive documents from older and newer versions of the app, and the two ways that
 * goes wrong are both asserted here rather than assumed.
 *
 * This repo has already paid for the first one. `LegacyCrashLogEntry.message` was nullable with no
 * default; Gson omitted nulls, kotlinx.serialization treats a missing key with no default as fatal,
 * and one crash entry without a message discarded a user's whole pre-merge log — silently, once per
 * install. A backup losing an entire recording history the same way would be considerably worse.
 */
class BackupWireCompatibilityTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @Test
    fun `a session document missing every optional key still decodes`() {
        val sessions =
            json.decodeFromString(
                ListSerializer(SessionDto.serializer()),
                // An older backup that predates folders, location, insights, modes and favourites.
                """
                [{"id":"s1","title":"Standup","tags":"work","audioFilePath":"/data/x.m4a",
                  "durationMs":61000,"fileSizeBytes":998000,"audioFormat":"AAC","sampleRateHz":48000,
                  "encodingBitRate":128000,"channelCount":1,"waveformSamples":"",
                  "status":"TRANSCRIBED","isArchived":false,"createdAt":1740000000000,
                  "updatedAt":1740000000000}]
                """.trimIndent(),
            )
        val session = sessions.single()
        assertEquals("Standup", session.title)
        assertNull(session.folderId)
        assertNull(session.locationLat)
        assertNull(session.sentimentJson)
        // Absent entirely, so these must arrive as the declared defaults rather than failing.
        assertEquals("JOURNAL", session.mode)
        assertEquals(false, session.isFavorite)
    }

    @Test
    fun `a session document carrying an unknown field from a newer build is not fatal`() {
        val sessions =
            json.decodeFromString(
                ListSerializer(SessionDto.serializer()),
                """
                [{"id":"s2","title":"Later","somethingAddedInAFutureRelease":{"nested":true},
                  "createdAt":1750000000000}]
                """.trimIndent(),
            )
        assertEquals("Later", sessions.single().title)
    }

    @Test
    fun `an empty database document decodes to empty lists rather than failing`() {
        val database = json.decodeFromString(DatabaseDto.serializer(), "{}")
        assertTrue(database.sessions.isEmpty())
        assertTrue(database.transcripts.isEmpty())
        assertTrue(database.providerConfigs.isEmpty())
    }

    @Test
    fun `a database document with only some tables present decodes`() {
        val database =
            json.decodeFromString(
                DatabaseDto.serializer(),
                """{"sessions":[{"id":"s3"}],"folders":[{"id":"f1","name":"Work"}]}""",
            )
        assertEquals("s3", database.sessions.single().id)
        assertEquals("Work", database.folders.single().name)
        assertTrue(database.speakerSegments.isEmpty())
    }

    @Test
    fun `a manifest from an older format decodes with defaults`() {
        val manifest = json.decodeFromString(BackupManifest.serializer(), """{"formatVersion":1,"createdAtMs":1740000000000}""")
        assertEquals(1, manifest.formatVersion)
        assertEquals(0, manifest.databaseSchemaVersion)
        assertEquals(0, manifest.sessionCount)
    }

    @Test
    fun `what this build writes decodes back unchanged`() {
        val original =
            DatabaseDto(
                sessions =
                    listOf(
                        SessionDto(
                            id = "s4",
                            title = "Retro",
                            tags = "work,team",
                            audioFilePath = "/data/data/dev.scrybe.android/files/recordings/recording_abc.m4a",
                            durationMs = 1_800_000,
                            fileSizeBytes = 28_000_000,
                            status = "TRANSCRIBED",
                            isFavorite = true,
                            createdAt = 1_750_000_000_000,
                            updatedAt = 1_750_000_000_001,
                        ),
                    ),
                transcripts = listOf(TranscriptDto(id = "t1", sessionId = "s4", content = "hello", createdAt = 1)),
                providerConfigs = listOf(ProviderConfigDto(id = "p1", providerType = "OPENAI", isEnabled = true, modelName = "whisper-1")),
            )
        val encoded = json.encodeToString(DatabaseDto.serializer(), original)
        assertEquals(original, json.decodeFromString(DatabaseDto.serializer(), encoded))
    }

    /**
     * The alias names a key in the secure store, which a backup does not carry. Restoring it would
     * leave a provider looking configured and failing on first use, so it is dropped on the way in
     * and comes back empty on the way out.
     */
    @Test
    fun `a restored provider config has no api key alias`() {
        val entity = ProviderConfigDto(id = "p2", providerType = "OPENAI", isEnabled = true, modelName = "gpt-4o-mini").toEntity()
        assertEquals("", entity.apiKeyAlias)
    }
}
