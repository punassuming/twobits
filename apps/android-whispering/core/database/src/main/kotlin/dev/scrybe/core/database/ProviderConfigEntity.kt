package dev.scrybe.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey val id: String,
    val providerType: String,
    val isEnabled: Boolean,
    val modelName: String,
    val apiKeyAlias: String,
)
