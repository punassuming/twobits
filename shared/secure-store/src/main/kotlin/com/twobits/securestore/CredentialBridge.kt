package com.twobits.securestore

/** Per-app bridge that maps [SharedCredentialId] to the app's own encrypted key store. */
interface CredentialBridge {
    suspend fun get(id: SharedCredentialId): String?
    suspend fun set(id: SharedCredentialId, value: String)
    suspend fun clear(id: SharedCredentialId)
}
