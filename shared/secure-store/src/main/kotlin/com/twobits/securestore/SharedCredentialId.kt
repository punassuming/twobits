package com.twobits.securestore

/** Registry of credentials shared transparently across all TwoBits apps. */
enum class SharedCredentialId(val wireId: String) {
    OPENAI("openai"),
    JINA("jina"),
    ;

    companion object {
        fun fromWireId(wireId: String): SharedCredentialId? = entries.firstOrNull { it.wireId == wireId }
    }
}
