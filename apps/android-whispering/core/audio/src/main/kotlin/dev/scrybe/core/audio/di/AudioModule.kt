package dev.scrybe.core.audio.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.scrybe.core.audio.AndroidMediaRecorder
import dev.scrybe.core.audio.AndroidMediaPlayer
import dev.scrybe.core.audio.AudioPlayer
import dev.scrybe.core.audio.AudioRecorder
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindsAudioRecorder(impl: AndroidMediaRecorder): AudioRecorder

    @Binds
    @Singleton
    abstract fun bindsAudioPlayer(impl: AndroidMediaPlayer): AudioPlayer
}
