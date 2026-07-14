package com.twobits.pricedrop.data.provider.serper

import com.google.gson.Gson
import com.google.gson.JsonArray
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SerperShoppingProvider
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val gson: Gson,
        private val settings: ProviderSettingsStore,
    ) : ProductSearchProvider {
        override val descriptor =
            ProviderDescriptor(
                id = "serper",
                displayName = "Serper Google Shopping",
                capabilities = setOf(ProviderCapability.SEARCH, ProviderCapability.OFFERS, ProviderCapability.PROMOTIONS),
            )

        override suspend fun search(request: ProductSearchRequest): ProviderResult<List<ProductCandidate>> =
            runCatching {
                val key = settings.getKey(PriceDropProvider.SERPER).trim()
                require(key.isNotEmpty()) { "Serper credential is missing" }
                withContext(Dispatchers.IO) {
                    val body =
                        JsonObject().apply {
                            addProperty("q", request.query)
                            addProperty("gl", request.country.lowercase())
                            addProperty("hl", request.locale.substringBefore('-').lowercase())
                            addProperty("num", request.maxCandidates)
                        }
                    val httpRequest =
                        Request
                            .Builder()
                            .url("https://google.serper.dev/shopping")
                            .addHeader("X-API-KEY", key)
                            .post(body.toString().toRequestBody(JSON_MEDIA))
                            .build()
                    client.newCall(httpRequest).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IOException("Serper returned HTTP ${response.code}")
                        val root = gson.fromJson(text, JsonObject::class.java)
                        (root.getAsJsonArray("shopping") ?: JsonArray())
                            .take(request.maxCandidates)
                            .mapNotNull { ShoppingMapper.map(it.asJsonObject, descriptor.id, "BYOK") }
                    }
                }
            }.fold(
                onSuccess = { ProviderResult.Success(it) },
                onFailure = { ProviderResult.Failure(it.message ?: "Serper search failed", it) },
            )

        private companion object {
            val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        }
    }
