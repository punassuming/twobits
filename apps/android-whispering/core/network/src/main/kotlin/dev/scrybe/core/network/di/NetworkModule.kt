package dev.scrybe.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun providesNetworkJson(): Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @Provides
    @Singleton
    fun providesOkHttpClient(): OkHttpClient {
        val loggingInterceptor =
            HttpLoggingInterceptor().apply {
                level =
                    if (dev.scrybe.core.network.BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
            }
        return OkHttpClient
            .Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .readTimeout(RESPONSE_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .callTimeout(CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()
    }

    private const val CONNECT_TIMEOUT_SECONDS = 30L
    private const val UPLOAD_TIMEOUT_MINUTES = 15L
    private const val RESPONSE_TIMEOUT_MINUTES = 20L
    private const val CALL_TIMEOUT_MINUTES = 35L
}
