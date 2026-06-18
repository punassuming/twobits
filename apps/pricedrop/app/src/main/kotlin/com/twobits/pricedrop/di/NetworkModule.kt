package com.twobits.pricedrop.di

import com.google.gson.Gson
import com.twobits.network.OkHttpClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClientFactory.create(debug = false)

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
