package com.shelfsnap.app.di

import com.shelfsnap.app.data.local.LocalModelManager
import com.twobits.localai.LlmDownloadSource
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
