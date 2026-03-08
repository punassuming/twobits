package dev.scrybe.core.transforms

import dev.scrybe.core.model.ProviderType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransformationPipeline @Inject constructor(
    private val providers: Map<ProviderType, @JvmSuppressWildcards TransformationProvider>
) {
    suspend fun execute(input: TransformInput, providerType: ProviderType): Result<TransformResult> {
        val provider = providers[providerType]
            ?: return Result.failure(IllegalArgumentException("No transformation provider for $providerType"))
        return provider.transform(input)
    }
}
