package dev.scrybe.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.scrybe.core.database.AppDatabase
import dev.scrybe.core.database.MIGRATION_4_5
import dev.scrybe.core.database.MIGRATION_5_6
import dev.scrybe.core.database.MIGRATION_6_7
import dev.scrybe.core.database.MIGRATION_7_8
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                "scrybe-db",
            ).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .fallbackToDestructiveMigrationFrom(1, 2, 3)
            .build()

    @Provides
    fun providesRecordingSessionDao(database: AppDatabase) = database.recordingSessionDao()

    @Provides
    fun providesTranscriptDao(database: AppDatabase) = database.transcriptDao()

    @Provides
    fun providesTransformProfileDao(database: AppDatabase) = database.transformProfileDao()

    @Provides
    fun providesTransformRunDao(database: AppDatabase) = database.transformRunDao()

    @Provides
    fun providesProviderConfigDao(database: AppDatabase) = database.providerConfigDao()

    @Provides
    fun providesFolderDao(database: AppDatabase) = database.folderDao()

    @Provides
    fun providesSpeakerSegmentDao(database: AppDatabase) = database.speakerSegmentDao()

    @Provides
    fun providesPersonDao(database: AppDatabase) = database.personDao()
}
