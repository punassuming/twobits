package dev.scrybe.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecordingSessionEntity::class,
        TranscriptEntity::class,
        TransformProfileEntity::class,
        TransformRunEntity::class,
        ProviderConfigEntity::class,
        FolderEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingSessionDao(): RecordingSessionDao

    abstract fun transcriptDao(): TranscriptDao

    abstract fun transformProfileDao(): TransformProfileDao

    abstract fun transformRunDao(): TransformRunDao

    abstract fun providerConfigDao(): ProviderConfigDao

    abstract fun folderDao(): FolderDao
}
