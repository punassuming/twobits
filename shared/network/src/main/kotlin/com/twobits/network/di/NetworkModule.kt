package com.twobits.network.di

import com.twobits.network.BuildConfig
import com.twobits.network.OkHttpClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun providesOkHttpClient(): OkHttpClient = OkHttpClientFactory.create(debug = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun providesNetworkJson(): Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
}
