package com.twobits.debuglogui

import androidx.lifecycle.ViewModel
import com.twobits.debuglog.BreadcrumbStore
import com.twobits.debuglog.DebugLogEntry
import com.twobits.debuglog.DebugLogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Backs each app's own crash-warning dialog. Only the ViewModel is shared: the dialog's wording
 * names the app and the specific feature that died ("on-device transcription" vs "Ask" vs
 * "analysis"), so `CrashWarningDialog` stays per-app while this — which was byte-identical in all
 * three — does not.
 *
 * Also the shared entry point for navigation breadcrumbs ([recordBreadcrumb]): every app already
 * injects one instance of this ViewModel at its NavHost's root, which is exactly the scope a
 * `NavController.addOnDestinationChangedListener` needs to live at, so this avoids adding a
 * second near-identical root-level ViewModel per app just to reach [BreadcrumbStore].
 */
@HiltViewModel
class CrashWarningViewModel
    @Inject
    constructor(
        private val debugLogStore: DebugLogStore,
        private val breadcrumbStore: BreadcrumbStore,
    ) : ViewModel() {
        val staleStartWarning: StateFlow<DebugLogEntry?> = debugLogStore.staleStartWarning

        fun dismiss() = debugLogStore.dismissStaleStartWarning()

        fun recordBreadcrumb(event: String) = breadcrumbStore.record(event)
    }
