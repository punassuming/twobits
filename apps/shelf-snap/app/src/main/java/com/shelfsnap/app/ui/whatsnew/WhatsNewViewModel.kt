package com.shelfsnap.app.ui.whatsnew

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.design.components.WhatsNewPopupCoordinator
import com.twobits.design.components.WhatsNewPopupState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhatsNewViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dataStore: DataStore<Preferences>,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(WhatsNewPopupState())
        val uiState: StateFlow<WhatsNewPopupState> = _uiState.asStateFlow()

        private var currentVersionCode: Long = 0L

        private val coordinator =
            WhatsNewPopupCoordinator(
                loadChangelogText = { loadChangelogText() },
                readLastSeenVersionCode = {
                    dataStore.data
                        .map { it[LAST_SEEN_KEY] ?: it[LAST_SEEN_KEY_LEGACY] ?: 0L }
                        .first()
                },
                writeLastSeenVersionCode = { versionCode ->
                    dataStore.edit {
                        it[LAST_SEEN_KEY] = versionCode
                        it.remove(LAST_SEEN_KEY_LEGACY)
                    }
                },
            )

        init {
            viewModelScope.launch {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                currentVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
                _uiState.value =
                    coordinator.computeInitialState(
                        currentVersionCode = currentVersionCode,
                        versionName = packageInfo.versionName.orEmpty(),
                    )
            }
        }

        fun dismiss() {
            viewModelScope.launch {
                coordinator.dismiss(currentVersionCode)
                _uiState.value = _uiState.value.copy(isVisible = false)
            }
        }

        private fun loadChangelogText(): String? =
            runCatching {
                context.assets
                    .open("CHANGELOG.md")
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrNull()

        private companion object {
            val LAST_SEEN_KEY = longPreferencesKey("whats_new_last_seen_version_code")

            // Legacy key — kept only as a one-time migration fallback so upgrading users
            // don't see a spurious re-prompt of the What's New popup.
            val LAST_SEEN_KEY_LEGACY = longPreferencesKey("ss_last_seen_version_code")
        }
    }
