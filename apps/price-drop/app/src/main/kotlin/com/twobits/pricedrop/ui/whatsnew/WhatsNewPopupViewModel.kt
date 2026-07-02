package com.twobits.pricedrop.ui.whatsnew

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.core.content.pm.PackageInfoCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.design.components.WhatsNewCategory
import com.twobits.design.components.WhatsNewItem
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
class WhatsNewPopupViewModel
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
                welcomeTitle = "Welcome to PriceDrop",
                firstRunCategory = firstRunCategory(),
                loadChangelogText = { loadChangelogText() },
                readLastSeenVersionCode = { dataStore.data.map { it[LAST_SEEN_KEY] ?: 0L }.first() },
                writeLastSeenVersionCode = { versionCode -> dataStore.edit { it[LAST_SEEN_KEY] = versionCode } },
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

        private fun firstRunCategory(): WhatsNewCategory =
            WhatsNewCategory(
                id = "getting_started",
                label = "Getting started",
                icon = Icons.Filled.RocketLaunch,
                items =
                    listOf(
                        "Add products from the Watch screen — paste a link, scan a barcode, or search by name.",
                        "PriceDrop checks prices in the background and notifies you when they drop.",
                        "Ask AI answers quick shopping questions about any tracked product.",
                        "AI configuration (Settings → AI) lets you use your own provider keys or PriceDrop Pro.",
                        "PriceDrop Pro unlocks managed AI and faster background checks — no personal API key needed.",
                    ).mapIndexed { index, text ->
                        WhatsNewItem(
                            id = "welcome_$index",
                            icon = Icons.Filled.RocketLaunch,
                            title = text,
                            description = "",
                        )
                    },
            )

        private companion object {
            val LAST_SEEN_KEY = longPreferencesKey("last_seen_whats_new_version_code")
        }
    }
