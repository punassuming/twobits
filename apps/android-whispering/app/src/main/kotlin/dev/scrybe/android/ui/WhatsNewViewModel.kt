package dev.scrybe.android.ui

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.scrybe.core.common.ReleaseNotes
import dev.scrybe.core.common.ReleaseNotesParser
import dev.scrybe.core.datastore.AppPreferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WhatsNewUiState(
    val isVisible: Boolean = false,
    val title: String = "",
    val versionName: String = "",
    val notes: List<String> = emptyList(),
)

@HiltViewModel
class WhatsNewViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferencesDataStore: AppPreferencesDataStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(WhatsNewUiState())
        val uiState: StateFlow<WhatsNewUiState> = _uiState.asStateFlow()

        private var currentVersionCode: Long = 0L

        init {
            viewModelScope.launch {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                currentVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
                val seenVersionCode = preferencesDataStore.lastSeenWhatsNewVersionCode.first()
                val releaseNotes = loadReleaseNotes() ?: return@launch

                if (currentVersionCode > seenVersionCode && releaseNotes.summaryItems.isNotEmpty()) {
                    _uiState.value =
                        WhatsNewUiState(
                            isVisible = true,
                            title = releaseNotes.title,
                            versionName = packageInfo.versionName.orEmpty(),
                            notes = releaseNotes.summaryItems,
                        )
                }
            }
        }

        fun dismiss() {
            viewModelScope.launch {
                preferencesDataStore.setLastSeenWhatsNewVersionCode(currentVersionCode)
                _uiState.value = _uiState.value.copy(isVisible = false)
            }
        }

        private fun loadReleaseNotes(): ReleaseNotes? {
            val changelogText =
                runCatching {
                    context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
                }.getOrNull() ?: return null
            return ReleaseNotesParser.parseLatestReleaseNotes(changelogText)
        }
    }
