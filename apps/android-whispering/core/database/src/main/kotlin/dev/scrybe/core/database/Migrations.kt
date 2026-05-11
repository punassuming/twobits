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

val MIGRATION_8_9: Migration =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `transcript_chunks` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `chunkIndex` INTEGER NOT NULL, `totalChunks` INTEGER NOT NULL, `status` TEXT NOT NULL, `text` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`sessionId`) REFERENCES `recording_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_transcript_chunks_sessionId` ON `transcript_chunks` (`sessionId`)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_transcript_chunks_sessionId_chunkIndex` ON `transcript_chunks` (`sessionId`, `chunkIndex`)",
            )
        }
    }

val MIGRATION_7_8: Migration =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE recording_sessions ADD COLUMN locationLat REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE recording_sessions ADD COLUMN locationLng REAL DEFAULT NULL")
            db.execSQL("ALTER TABLE recording_sessions ADD COLUMN locationLabel TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE recording_sessions ADD COLUMN sentimentJson TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE recording_sessions ADD COLUMN topicsJson TEXT DEFAULT NULL")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `speaker_segments` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `speakerId` TEXT NOT NULL, `speakerLabel` TEXT, `personId` TEXT, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`sessionId`) REFERENCES `recording_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_speaker_segments_sessionId` ON `speaker_segments` (`sessionId`)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `persons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `voiceEmbeddingJson` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
        }
    }
