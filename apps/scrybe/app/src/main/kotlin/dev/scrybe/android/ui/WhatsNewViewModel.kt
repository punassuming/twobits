package dev.scrybe.android.ui

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    }
