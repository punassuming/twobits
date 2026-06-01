package dev.scrybe.core.localai.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dev.scrybe.core.localai.DiarizationServiceFacade
import dev.scrybe.core.localai.InsightServiceFacade
import dev.scrybe.core.localai.LocalTransformationProvider
import dev.scrybe.core.localai.WhisperTranscriptionProvider
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transcription.DiarizationService
import dev.scrybe.core.transcription.InsightService
import dev.scrybe.core.transcription.TranscriptionProvider
import dev.scrybe.core.transcription.di.ProviderTypeKey
import dev.scrybe.core.transforms.TransformationProvider
import dev.scrybe.core.transforms.di.ProviderTypeKey as TransformProviderTypeKey

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalAiModule {
    @Binds
    @IntoMap
    @ProviderTypeKey(ProviderType.LOCAL)
    abstract fun bindsLocalTranscriptionProvider(impl: WhisperTranscriptionProvider): TranscriptionProvider

    @Binds
    @IntoMap
    @TransformProviderTypeKey(ProviderType.LOCAL)
    abstract fun bindsLocalTransformationProvider(impl: LocalTransformationProvider): TransformationProvider

    @Binds
    abstract fun bindsDiarizationService(impl: DiarizationServiceFacade): DiarizationService

    @Binds
    abstract fun bindsInsightService(impl: InsightServiceFacade): InsightService
}
