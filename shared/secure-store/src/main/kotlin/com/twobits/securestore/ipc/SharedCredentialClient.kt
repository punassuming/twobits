package com.twobits.securestore.ipc

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.twobits.securestore.SharedCredentialId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val METHOD_GET = "get"
private const val METHOD_SET = "set"
private const val EXTRA_ID = "id"
private const val EXTRA_VALUE = "value"

private val SIBLING_APP_IDS = listOf(
    "dev.scrybe.android",
    "com.shelfsnap.app",
    "com.twobits.pricedrop",
)

/**
 * Transparently reads/writes shared credentials across sibling TwoBits apps.
 *
 * - readThrough(id): returns the first non-blank value found in an installed sibling
 * - mirror(id, value): writes to every installed sibling
 *
 * Never throws — SecurityException (signature mismatch in dev), unknown authority (not installed),
 * and null Bundle are all silently swallowed.
 */
@Singleton
class SharedCredentialClient
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private fun siblingUri(appId: String) = Uri.parse("content://$appId.credentials")

        private fun callSibling(
            appId: String,
            method: String,
            extras: Bundle,
        ): Bundle? =
            try {
                context.contentResolver.call(siblingUri(appId), method, null, extras)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                null
            }

        private fun ownAppId(): String = context.packageName

        suspend fun readThrough(id: SharedCredentialId): String? =
            withContext(Dispatchers.IO) {
                val extras = Bundle().apply { putString(EXTRA_ID, id.wireId) }
                SIBLING_APP_IDS
                    .filter { it != ownAppId() }
                    .firstNotNullOfOrNull { appId ->
                        callSibling(appId, METHOD_GET, extras)
                            ?.getString(EXTRA_VALUE)
                            ?.takeIf { it.isNotBlank() }
                    }
            }

        suspend fun mirror(
            id: SharedCredentialId,
            value: String,
        ) = withContext(Dispatchers.IO) {
            val extras = Bundle().apply {
                putString(EXTRA_ID, id.wireId)
                putString(EXTRA_VALUE, value)
            }
            SIBLING_APP_IDS
                .filter { it != ownAppId() }
                .forEach { appId -> callSibling(appId, METHOD_SET, extras) }
        }
    }
