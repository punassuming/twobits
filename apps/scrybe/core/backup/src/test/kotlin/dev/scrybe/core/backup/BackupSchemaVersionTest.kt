package dev.scrybe.core.backup

import androidx.room.Database
import dev.scrybe.core.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `BackupWorker.DATABASE_SCHEMA_VERSION` duplicates `AppDatabase`'s version number so the manifest
 * can record it without `:core:backup` depending on the database class itself.
 *
 * Duplication of a number that changes on every migration is exactly the kind that rots quietly:
 * nothing breaks when they drift, backups simply start claiming the wrong schema, and restore's
 * "made by a newer version" guard stops meaning anything. Reading the real value off the `@Database`
 * annotation makes the drift a failing test instead.
 */
class BackupSchemaVersionTest {
    @Test
    fun `the version the manifest records matches the database's own`() {
        val annotation =
            AppDatabase::class.java.getAnnotation(Database::class.java)
                ?: error("AppDatabase is no longer annotated with @Database")
        assertEquals(
            "BackupWorker.DATABASE_SCHEMA_VERSION is out of date. Room is on version " +
                "${annotation.version}; update the constant so backups record the right schema and " +
                "restore's newer-version guard keeps working.",
            annotation.version,
            BackupWorker.DATABASE_SCHEMA_VERSION,
        )
    }
}
