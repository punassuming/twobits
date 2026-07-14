package com.twobits.pricedrop.data.provider.searchapi

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.twobits.pricedrop.data.provider.PriceDropProvider
import com.twobits.pricedrop.data.provider.ProviderSettingsStore
import com.twobits.pricedrop.data.provider.contracts.ProductSearchProvider
import com.twobits.pricedrop.data.provider.contracts.ProductSearchRequest
import com.twobits.pricedrop.data.provider.contracts.ProviderCapability
import com.twobits.pricedrop.data.provider.contracts.ProviderDescriptor
import com.twobits.pricedrop.data.provider.contracts.ProviderResult
import com.twobits.pricedrop.data.provider.contracts.ShoppingMapper
import com.twobits.pricedrop.domain.product.ProductCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchApiShoppingProvider
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val gson: Gson,
        private val settings: ProviderSettingsStore,
    ) : ProductSearchProvider {
        override val descriptor =
            ProviderDescriptor(
                id = "searchapi",
                displayName = "SearchAPI Google Shopping",
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.OFFERS, ProviderCapability.PROMOTIONS),
            )

        override suspend fun search(request: ProductSearchRequest): ProviderResult<List<ProductCandidate>> =
            runCatching {
                val key = settings.getKey(PriceDropProvider.SHOPPING).trim()
                require(key.isNotEmpty()) { "SearchAPI credential is missing" }
                withContext(Dispatchers.IO) {
                    val url =
                        "https://www.searchapi.io/api/v1/search"
                            .toHttpUrl()
                            .newBuilder()
                            .addQueryParameter("engine", "google_shopping")
                            .addQueryParameter("q", request.query)
                            .addQueryParameter("api_key", key)
                            .addQueryParameter("num", request.maxCandidates.toString())
                            .build()
                    client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IOException("SearchAPI returned HTTP ${response.code}")
                        val root = gson.fromJson(text, JsonObject::class.java)
                        sequenceOf("shopping_results", "popular_products")
                            .mapNotNull(root::getAsJsonArray)
                            .flatMap { it.asSequence() }
                            .take(request.maxCandidates)
                            .mapNotNull { ShoppingMapper.map(it.asJsonObject, descriptor.id, "BYOK") }
                            .toList()
                    }
                }
            }.fold(
                onSuccess = { ProviderResult.Success(it) },
                onFailure = { ProviderResult.Failure(it.message ?: "SearchAPI search failed", it) },
            )
    }
