package dev.scrybe.core.transforms

import dev.scrybe.core.model.ProviderType

interface TransformationProvider {
    val providerType: ProviderType

    suspend fun transform(input: TransformInput): Result<TransformResult>
}
