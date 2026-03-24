package dev.scrybe.core.transforms.di

import dagger.Module
import dagger.Binds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.Multibinds
import dev.scrybe.core.model.ProviderType
import dev.scrybe.core.transforms.OpenAiTransformationProvider
import dev.scrybe.core.transforms.TransformationProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class TransformsModule {

    /**
     * Provides an empty map of transformation providers as a starting point.
     * Concrete providers should use @Binds @IntoMap to register themselves.
     */
    @Multibinds
    abstract fun bindsTransformationProviders(): Map<ProviderType, TransformationProvider>

    @Binds
    @IntoMap
    @ProviderTypeKey(ProviderType.OPENAI)
    abstract fun bindsOpenAiProvider(impl: OpenAiTransformationProvider): TransformationProvider
}
