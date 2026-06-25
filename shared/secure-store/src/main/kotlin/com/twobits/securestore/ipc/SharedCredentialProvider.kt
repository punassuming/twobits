package com.twobits.securestore.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.twobits.securestore.CredentialBridge
import com.twobits.securestore.SharedCredentialId
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

private const val METHOD_GET = "get"
private const val METHOD_SET = "set"
private const val METHOD_CLEAR = "clear"
private const val EXTRA_ID = "id"
private const val EXTRA_VALUE = "value"

/**
 * Signature-gated ContentProvider that exposes the local encrypted credential store to sibling
 * TwoBits apps. Returns/accepts plaintext values — each app re-encrypts under its own keystore key.
 *
 * ContentProvider.onCreate() fires before Hilt is ready, so the bridge is resolved lazily via
 * EntryPointAccessors in call() instead of via field injection.
 */
class SharedCredentialProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CredentialBridgeEntryPoint {
        fun credentialBridge(): CredentialBridge
    }

    private fun bridge(): CredentialBridge =
        EntryPointAccessors
            .fromApplication(context!!.applicationContext, CredentialBridgeEntryPoint::class.java)
            .credentialBridge()

    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        val wireId = extras?.getString(EXTRA_ID) ?: return null
        val id = SharedCredentialId.fromWireId(wireId) ?: return null
        return when (method) {
            METHOD_GET -> {
                val value = runBlocking { bridge().get(id) }
                Bundle().apply { putString(EXTRA_VALUE, value) }
            }
            METHOD_SET -> {
                val value = extras.getString(EXTRA_VALUE) ?: return null
                runBlocking { bridge().set(id, value) }
                Bundle()
            }
            METHOD_CLEAR -> {
                runBlocking { bridge().clear(id) }
                Bundle()
            }
            else -> null
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
