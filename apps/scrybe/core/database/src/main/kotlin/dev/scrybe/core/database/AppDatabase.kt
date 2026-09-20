package dev.scrybe.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The schema version, named so other modules can record it without depending on [AppDatabase].
 *
 * `:core:backup` writes it into every backup's manifest, and restore refuses a backup claiming a
 * newer one. That guard is only meaningful if the number is the real one, so it lives here and is
 * referenced rather than copied — a second literal would drift on the next migration with nothing
 * failing to say so.
 */
const val SCRYBE_DATABASE_VERSION = 17

@Database(
    entities = [
        RecordingSessionEntity::class,
        TranscriptEntity::class,
        TransformProfileEntity::class,
        TransformRunEntity::class,
        ProviderConfigEntity::class,
        FolderEntity::class,
        SpeakerSegmentEntity::class,
        PersonEntity::class,
        TranscriptChunkEntity::class,
        SessionTaskEntity::class,
        CustomRecordingTypeEntity::class,
    ],
    version = SCRYBE_DATABASE_VERSION,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingSessionDao(): RecordingSessionDao

    abstract fun transcriptDao(): TranscriptDao

    abstract fun transformProfileDao(): TransformProfileDao

    abstract fun transformRunDao(): TransformRunDao

    abstract fun providerConfigDao(): ProviderConfigDao

    abstract fun folderDao(): FolderDao

    abstract fun speakerSegmentDao(): SpeakerSegmentDao

    abstract fun personDao(): PersonDao

    abstract fun transcriptChunkDao(): TranscriptChunkDao

    abstract fun sessionTaskDao(): SessionTaskDao

    abstract fun customRecordingTypeDao(): CustomRecordingTypeDao
}
