package dev.scrybe.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 4 to 5: adds the `tags` column to recording_sessions.
 *
 * Previously the database used [fallbackToDestructiveMigration] which silently
 * dropped every table when the schema version changed.  This migration preserves
 * existing data instead.
 */
val MIGRATION_4_5: Migration =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE recording_sessions ADD COLUMN tags TEXT NOT NULL DEFAULT ''",
            )
        }
    }

val MIGRATION_5_6: Migration =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS folders (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    parentFolderId TEXT,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "ALTER TABLE recording_sessions ADD COLUMN folderId TEXT DEFAULT NULL",
            )
        }
    }

val MIGRATION_6_7: Migration =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE transform_profiles ADD COLUMN modelName TEXT",
            )
        }
    }
