package com.twobits.pricedrop.data.provider.registry

import com.twobits.pricedrop.data.provider.AiFeature
import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderMode
import com.twobits.pricedrop.data.provider.ProviderSettingsStore
import com.twobits.pricedrop.data.provider.contracts.OfferProvider
import com.twobits.pricedrop.data.provider.contracts.ProductDetailsProvider
import com.twobits.pricedrop.data.provider.contracts.ProductSearchProvider
import com.twobits.pricedrop.data.provider.contracts.PromotionProvider
import com.twobits.pricedrop.data.provider.jina.JinaWebProvider
import com.twobits.pricedrop.data.provider.pro.ProGatewayProvider
import com.twobits.pricedrop.data.provider.rainforest.RainforestAmazonProvider
import com.twobits.pricedrop.data.provider.searchapi.SearchApiShoppingProvider
import com.twobits.pricedrop.data.provider.serper.SerperShoppingProvider
import javax.inject.Inject
import javax.inject.Singleton

interface ProviderRegistry {
    suspend fun searchProviders(): List<ProductSearchProvider>

    suspend fun detailProviders(): List<ProductDetailsProvider>

    suspend fun offerProviders(): List<OfferProvider>

    suspend fun promotionProviders(): List<PromotionProvider>
}

@Singleton
class DefaultProviderRegistry
    @Inject
    constructor(
        private val settings: ProviderSettingsStore,
        private val serper: SerperShoppingProvider,
        private val searchApi: SearchApiShoppingProvider,
        private val jina: JinaWebProvider,
        private val pro: ProGatewayProvider,
        private val rainforest: RainforestAmazonProvider,
    ) : ProviderRegistry {
        override suspend fun searchProviders(): List<ProductSearchProvider> {
            if (settings.getFeatureSource(AiFeature.SEARCH) == ProviderMode.PRO) return listOf(pro)
            val enabled = settings.getFeatureProviders(AiFeature.SEARCH)
            return buildList {
                addIfEnabled(PriceDropProvider.SERPER, enabled, serper)
                addIfEnabled(PriceDropProvider.SHOPPING, enabled, searchApi)
                addIfEnabled(PriceDropProvider.WEB_SEARCH, enabled, jina)
            }
        }

        override suspend fun detailProviders(): List<ProductDetailsProvider> = rainforestIfEnabled()

        override suspend fun offerProviders(): List<OfferProvider> = rainforestIfEnabled()

        override suspend fun promotionProviders(): List<PromotionProvider> = emptyList()

        private suspend fun <T> rainforestIfEnabled(): List<T> =
            if (settings.getMode(PriceDropProvider.RAINFOREST) == ProviderMode.OFF) {
                emptyList()
            } else {
                @Suppress("UNCHECKED_CAST")
                listOf(rainforest as T)
            }

        private suspend fun MutableList<ProductSearchProvider>.addIfEnabled(
            provider: PriceDropProvider,
            enabled: Set<String>,
            adapter: ProductSearchProvider,
        ) {
            if (provider.key in enabled && settings.getMode(provider) == ProviderMode.BYOK) add(adapter)
        }
    }
