package com.twobits.pricedrop.di

import com.twobits.localai.LlmDownloadSource
import com.twobits.pricedrop.data.local.LocalModelManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalAiModule {
    @Binds
    abstract fun bindsLlmDownloadSource(impl: LocalModelManager): LlmDownloadSource
}
