package dev.scrybe.core.transcription.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transcription.ApiKeyProvider
import dev.scrybe.core.transcription.KeystoreApiKeyProvider
import dev.scrybe.core.transcription.OpenAiTranscriptionProvider
import dev.scrybe.core.transcription.TranscriptionProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class TranscriptionModule {
    @Binds
    @IntoMap
    @ProviderTypeKey(ProviderType.OPENAI)
    abstract fun bindsOpenAiProvider(impl: OpenAiTranscriptionProvider): TranscriptionProvider

    @Binds
    abstract fun bindsApiKeyProvider(impl: KeystoreApiKeyProvider): ApiKeyProvider
}
