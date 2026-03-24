package dev.scrybe.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.scrybe.core.database.AppDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "scrybe-db",
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providesRecordingSessionDao(database: AppDatabase) = database.recordingSessionDao()

    @Provides
    fun providesTranscriptDao(database: AppDatabase) = database.transcriptDao()

    @Provides
    fun providesTransformProfileDao(database: AppDatabase) = database.transformProfileDao()

    @Provides
    fun providesProviderConfigDao(database: AppDatabase) = database.providerConfigDao()
}
