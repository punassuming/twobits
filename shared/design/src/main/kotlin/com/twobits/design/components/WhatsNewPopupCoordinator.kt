package com.twobits.design.components

import com.twobits.common.ReleaseNotesParser

/** State for [AppWhatsNewDialog], produced by [WhatsNewPopupCoordinator]. */
data class WhatsNewPopupState(
    val isVisible: Boolean = false,
    val isFirstRun: Boolean = false,
    val title: String = "",
    val versionName: String = "",
    val categories: List<WhatsNewCategory> = emptyList(),
    val confirmLabel: String = "Close",
)

/**
 * App-agnostic "what's new" popup logic: compares the current app version code against the
 * last-seen one, loads and parses the latest release's changelog, and caps it to a size
 * suitable for a modal — all shared so this isn't reimplemented per app. Each app wraps this in
 * a thin Hilt `ViewModel` that supplies its own DataStore-backed last-seen-version storage and
 * changelog asset reader, since Hilt DI itself is per-app.
 */
class WhatsNewPopupCoordinator(
    private val welcomeTitle: String,
    private val firstRunCategory: WhatsNewCategory,
    private val loadChangelogText: () -> String?,
    private val readLastSeenVersionCode: suspend () -> Long,
    private val writeLastSeenVersionCode: suspend (Long) -> Unit,
) {
    suspend fun computeInitialState(
        currentVersionCode: Long,
        versionName: String,
    ): WhatsNewPopupState {
        val lastSeen = readLastSeenVersionCode()
        if (currentVersionCode <= lastSeen) return WhatsNewPopupState()

        if (lastSeen == 0L) {
            return WhatsNewPopupState(
                isVisible = true,
                isFirstRun = true,
                title = welcomeTitle,
                versionName = versionName,
                categories = listOf(firstRunCategory),
                confirmLabel = "Get started",
            )
        }

        val categories = loadLatestReleaseCategories()
        if (categories.isEmpty()) return WhatsNewPopupState()
        return WhatsNewPopupState(
            isVisible = true,
            title = "What's New in $versionName",
            versionName = versionName,
            categories = categories,
            confirmLabel = "Close",
        )
    }

    suspend fun dismiss(currentVersionCode: Long) {
        writeLastSeenVersionCode(currentVersionCode)
    }

    private fun loadLatestReleaseCategories(): List<WhatsNewCategory> {
        val text = loadChangelogText() ?: return emptyList()
        val latest = ReleaseNotesParser.parseLatestReleaseNotes(text) ?: return emptyList()
        return latest.toWhatsNewRelease(isLatest = true).categories.capToMaxItems(MAX_DIALOG_ITEMS)
    }

    private companion object {
        const val MAX_DIALOG_ITEMS = 6
    }
}

/** Caps the total item count across categories (preserving order), dropping any left empty. */
private fun List<WhatsNewCategory>.capToMaxItems(max: Int): List<WhatsNewCategory> {
    var remaining = max
    return mapNotNull { category ->
        if (remaining <= 0) return@mapNotNull null
        val taken = category.items.take(remaining)
        remaining -= taken.size
        taken.takeIf { it.isNotEmpty() }?.let { category.copy(items = it) }
    }
}
