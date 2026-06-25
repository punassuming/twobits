package com.twobits.pricedrop.credentials

import com.twobits.securestore.CredentialBridge
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CredentialBridgeModule {
    @Binds
    @Singleton
    abstract fun bindCredentialBridge(impl: PriceDropCredentialBridge): CredentialBridge
}
