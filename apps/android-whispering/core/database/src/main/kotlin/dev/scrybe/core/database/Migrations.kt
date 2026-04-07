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
