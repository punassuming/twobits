package dev.scrybe.android.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twobits.design.components.WhatsNewCategory
import com.twobits.design.components.WhatsNewItem
import com.twobits.design.components.WhatsNewPopupCoordinator
import com.twobits.design.components.WhatsNewPopupState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.datastore.AppPreferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhatsNewViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(WhatsNewPopupState())
        val uiState: StateFlow<WhatsNewPopupState> = _uiState.asStateFlow()

        private var currentVersionCode: Long = 0L

        private val coordinator =
            WhatsNewPopupCoordinator(
                welcomeTitle = "Welcome to Scrybe",
                firstRunCategory = firstRunCategory(),
                loadChangelogText = { loadChangelogText() },
                readLastSeenVersionCode = { preferencesDataStore.lastSeenWhatsNewVersionCode.first() },
                writeLastSeenVersionCode = { preferencesDataStore.setLastSeenWhatsNewVersionCode(it) },
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
                        "Start recording from the home screen and Scrybe saves the raw audio before any AI step runs.",
                        "Open a saved session to scrub the waveform, review transcript text, and jump directly to speaker turns.",
                        "Speaker identification and insight analysis can be turned on independently in Settings when you want richer review data.",
                        "Suggested tags turn recordings into searchable buckets for calls, meetings, interviews, and follow-up work.",
                        "Use post-processing profiles to transform raw transcripts into cleaner summaries, action items, or custom outputs.",
                        "Recent recordings stay one tap away, and archived sessions remain available without cluttering your active list.",
                    ).mapIndexed { index, text ->
                        WhatsNewItem(
                            id = "welcome_$index",
                            icon = Icons.Filled.RocketLaunch,
                            title = text,
                            description = "",
                        )
                    },
            )
    }
