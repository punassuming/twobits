package dev.scrybe.core.transcription.di

import dagger.MapKey
import dev.scrybe.core.model.ProviderType

@MapKey
annotation class ProviderTypeKey(val value: ProviderType)
