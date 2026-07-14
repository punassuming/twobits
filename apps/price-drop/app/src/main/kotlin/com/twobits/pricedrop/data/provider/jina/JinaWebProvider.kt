package com.twobits.pricedrop.data.provider.jina

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
import com.twobits.pricedrop.domain.product.ProductCandidate
import com.twobits.pricedrop.domain.product.ProviderRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JinaWebProvider
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val gson: Gson,
        private val settings: ProviderSettingsStore,
    ) : ProductSearchProvider {
        override val descriptor =
            ProviderDescriptor(
                id = "jina",
                displayName = "Jina web discovery",
                capabilities = setOf(ProviderCapability.SEARCH),
            )

        override suspend fun search(request: ProductSearchRequest): ProviderResult<List<ProductCandidate>> =
            runCatching {
                val key = settings.getKey(PriceDropProvider.WEB_SEARCH).trim()
                require(key.isNotEmpty()) { "Jina credential is missing" }
                withContext(Dispatchers.IO) {
                    val url = "https://s.jina.ai/".toHttpUrl().newBuilder().addQueryParameter("q", request.query).build()
                    val call =
                        Request
                            .Builder()
                            .url(url)
                            .addHeader("Authorization", "Bearer $key")
                            .addHeader("Accept", "application/json")
                            .addHeader("X-Return-Format", "json")
                            .get()
                            .build()
                    client.newCall(call).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (!response.isSuccessful) throw IOException("Jina returned HTTP ${response.code}")
                        val root = gson.fromJson(text, JsonObject::class.java)
                        (root.getAsJsonArray("data") ?: JsonArray()).take(request.maxCandidates).mapNotNull { element ->
                            val item = element.asJsonObject
                            val title = item["title"]?.asString ?: return@mapNotNull null
                            val sourceUrl = item["url"]?.asString ?: return@mapNotNull null
                            ProductCandidate(
                                provider = ProviderRef(descriptor.id, "BYOK"),
                                title = title,
                                sourceUrl = sourceUrl,
                            )
                        }
                    }
                }
            }.fold(
                onSuccess = { ProviderResult.Success(it) },
                onFailure = { ProviderResult.Failure(it.message ?: "Jina search failed", it) },
            )
    }
