package dev.scrybe.core.transcription.di

import com.twobits.securestore.CredentialBridge
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.scrybe.core.transcription.ScrybeCredentialBridge
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CredentialBridgeModule {
    @Binds
    @Singleton
    abstract fun bindCredentialBridge(impl: ScrybeCredentialBridge): CredentialBridge
}
