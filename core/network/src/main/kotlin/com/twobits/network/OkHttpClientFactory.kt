package com.twobits.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object OkHttpClientFactory {
    fun create(debug: Boolean): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (debug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30L, TimeUnit.SECONDS)
            .writeTimeout(15L, TimeUnit.MINUTES)
            .readTimeout(20L, TimeUnit.MINUTES)
            .callTimeout(35L, TimeUnit.MINUTES)
            .build()
    }
}
