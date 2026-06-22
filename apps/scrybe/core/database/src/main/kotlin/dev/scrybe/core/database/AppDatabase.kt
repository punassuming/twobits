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
        SpeakerSegmentEntity::class,
        PersonEntity::class,
        TranscriptChunkEntity::class,
        SessionTaskEntity::class,
        CustomRecordingTypeEntity::class,
    ],
    version = 15,
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
