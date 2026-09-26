package com.shelfsnap.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE items ADD COLUMN primaryPhotoIndex INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE items ADD COLUMN title TEXT NOT NULL DEFAULT ''",
            )
        }
    }

/** Nullable, no default: only LOCAL-mode analyses ever populate it — everything before this migration is null, which is exactly "unknown/not local." */
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE items ADD COLUMN executionMode TEXT",
            )
        }
    }
