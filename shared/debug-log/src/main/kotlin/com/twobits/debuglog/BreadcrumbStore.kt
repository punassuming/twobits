package com.twobits.debuglog

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class Breadcrumb(
    val timestampMs: Long,
    val event: String,
)

/**
 * A short, bounded trail of navigation/lifecycle events — "screen shown", "service started" —
 * kept separate from [DebugLogStore]'s AI-call-scoped JSON file rather than routed through
 * [DebugLogStore.record]: that file is re-parsed on every single write and guarded by a
 * cross-process [java.nio.channels.FileLock], sized for occasional AI-call entries, not
 * high-frequency breadcrumb traffic (a navigation transition on every screen change). This store
 * is main-process-only — breadcrumbs are UI/lifecycle events, never needed from the `:inference`
 * process the way [DebugLogStore] is — so it needs no cross-process locking at all.
 *
 * The trail is mirrored to [SharedPreferences] on every [record] call (a single cheap
 * `putString().apply()`, overwriting the same key — not a growing file) specifically so it
 * survives a crash: [DebugLogStore] reads [readPersisted] back on the *next* launch to attach the
 * events leading up to a crash to that crash's own log entry, which an in-memory-only trail could
 * never do, since the crash that makes the trail useful is exactly what would otherwise erase it.
 */
@Singleton
class BreadcrumbStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
        private val lock = Any()
        private var inMemory: List<Breadcrumb> = emptyList()

        fun record(event: String) {
            synchronized(lock) {
                inMemory = (inMemory + Breadcrumb(System.currentTimeMillis(), event)).takeLast(MAX_BREADCRUMBS)
                runCatching {
                    prefs()
                        .edit()
                        .putString(KEY_TRAIL, json.encodeToString(ListSerializer(Breadcrumb.serializer()), inMemory))
                        .apply()
                }.onFailure { Log.w(TAG, "Failed to persist breadcrumb: ${it.javaClass.simpleName}") }
            }
        }

        fun current(): List<Breadcrumb> = synchronized(lock) { inMemory }

        /** The trail as of the last [record] call from any process that has run since — including one that has since died. */
        fun readPersisted(): List<Breadcrumb> =
            runCatching {
                val raw = prefs().getString(KEY_TRAIL, null) ?: return emptyList()
                json.decodeFromString(ListSerializer(Breadcrumb.serializer()), raw)
            }.getOrElse { emptyList() }

        fun formatTrail(breadcrumbs: List<Breadcrumb>): String = breadcrumbs.joinToString(" → ") { it.event }

        private fun prefs(): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private companion object {
            const val TAG = "BreadcrumbStore"
            const val PREFS_NAME = "breadcrumb_store"
            const val KEY_TRAIL = "trail"
            const val MAX_BREADCRUMBS = 20
        }
    }
