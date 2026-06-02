package com.twobits.apikeys.di

import com.twobits.apikeys.ApiKeyProvider
import com.twobits.apikeys.KeystoreApiKeyProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ApiKeysModule {
    @Binds
    @Singleton
    abstract fun bindApiKeyProvider(impl: KeystoreApiKeyProvider): ApiKeyProvider
}
