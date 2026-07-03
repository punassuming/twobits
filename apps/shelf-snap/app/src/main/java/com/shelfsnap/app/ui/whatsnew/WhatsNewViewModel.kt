package com.shelfsnap.app.ui.whatsnew

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
                welcomeTitle = "Welcome to Shelf Snap",
                firstRunCategory = firstRunCategory(),
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

        private fun firstRunCategory(): WhatsNewCategory =
            WhatsNewCategory(
                id = "getting_started",
                label = "Getting started",
                icon = Icons.Filled.RocketLaunch,
                items =
                    listOf(
                        "Snap a photo of any item — AI identifies it and estimates resale value instantly.",
                        "The Inventory screen tracks everything: draft, listed, and sold items with estimates.",
                        "Item Detail shows a market price range and prepares eBay, Mercari, and OfferUp listings.",
                        "AI configuration (Settings → AI) lets you choose your vision model or use Shelf Snap Pro.",
                        "The Camera screen guides you to the best framing, then analyses in one tap.",
                        "Pro subscription unlocks the managed AI — no personal API key needed.",
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
            val LAST_SEEN_KEY = longPreferencesKey("whats_new_last_seen_version_code")

            // Legacy key — kept only as a one-time migration fallback so upgrading users
            // don't see a spurious re-prompt of the What's New popup.
            val LAST_SEEN_KEY_LEGACY = longPreferencesKey("ss_last_seen_version_code")
        }
    }
