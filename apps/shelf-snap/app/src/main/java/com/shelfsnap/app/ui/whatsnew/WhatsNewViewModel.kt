package com.shelfsnap.app.ui.whatsnew

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SSWhatsNewUiState(
    val isVisible: Boolean = false,
    val isFirstRun: Boolean = false,
    val title: String = "",
    val versionName: String = "",
    val notes: List<String> = emptyList(),
    val confirmLabel: String = "Close",
)

@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SSWhatsNewUiState())
    val uiState: StateFlow<SSWhatsNewUiState> = _uiState.asStateFlow()

    private var currentVersionCode: Long = 0L

    init {
        viewModelScope.launch {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            currentVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            val lastSeen = dataStore.data.map { it[LAST_SEEN_KEY] ?: 0L }.first()
            val isFirstRun = lastSeen == 0L

            if (currentVersionCode > lastSeen) {
                if (isFirstRun) {
                    _uiState.value = SSWhatsNewUiState(
                        isVisible = true,
                        isFirstRun = true,
                        title = "Welcome to Shelf Snap",
                        versionName = packageInfo.versionName.orEmpty(),
                        notes = firstRunNotes(),
                        confirmLabel = "Get started",
                    )
                } else {
                    val bullets = loadChangelogBullets()
                    if (bullets.isNotEmpty()) {
                        _uiState.value = SSWhatsNewUiState(
                            isVisible = true,
                            title = "What's New in ${packageInfo.versionName.orEmpty()}",
                            versionName = packageInfo.versionName.orEmpty(),
                            notes = bullets,
                            confirmLabel = "Close",
                        )
                    }
                }
            }
        }
    }

    fun dismiss() {
        viewModelScope.launch {
            dataStore.edit { it[LAST_SEEN_KEY] = currentVersionCode }
            _uiState.value = _uiState.value.copy(isVisible = false)
        }
    }

    private fun loadChangelogBullets(): List<String> {
        val text = runCatching {
            context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()

        val lines = text.lines()
        val firstVersionLine = lines.indexOfFirst { line ->
            line.startsWith("## ") && !line.contains("Unreleased", ignoreCase = true)
        }
        if (firstVersionLine == -1) return emptyList()

        val nextVersionLine = lines.drop(firstVersionLine + 1)
            .indexOfFirst { it.startsWith("## ") }
        val end = if (nextVersionLine == -1) lines.size else firstVersionLine + 1 + nextVersionLine

        return lines.subList(firstVersionLine, end)
            .asSequence()
            .map { it.trim() }
            .filter { it.startsWith("**") || it.startsWith("* ") || it.startsWith("- ") }
            .map { line ->
                when {
                    line.startsWith("**") -> line.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                        .trimEnd(':').trim()
                    else -> line.removePrefix("* ").removePrefix("- ").trim()
                }
            }
            .filter { it.isNotBlank() }
            .take(8)
            .toList()
    }

    private fun firstRunNotes(): List<String> = listOf(
        "Snap a photo of any item — AI identifies it and estimates resale value instantly.",
        "The Inventory screen tracks everything: draft, listed, and sold items with estimates.",
        "Item Detail shows a market price range and prepares eBay, Mercari, and OfferUp listings.",
        "AI configuration (Settings → AI) lets you choose your vision model or use Shelf Snap Pro.",
        "The Camera screen guides you to the best framing, then analyses in one tap.",
        "Pro subscription unlocks the managed AI — no personal API key needed.",
    )

    companion object {
        private val LAST_SEEN_KEY = longPreferencesKey("ss_last_seen_version_code")
    }
}
