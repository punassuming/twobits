package com.twobits.localai

import com.twobits.core.localmodels.LocalLlmModel

/**
 * Lets [LlmModelDownloadCoordinator] surface a download's outcome to whichever unified debug log
 * the calling app actually has. This module is shared across Scrybe, Shelf Snap, and PriceDrop
 * and can't depend on any single app's debug-log types directly — each app defines its own
 * (mirrored, separately-compiled) `DebugLogStore`/`DebugLogEntry` — so each app's
 * `LocalModelManager` supplies an implementation that wraps its own store.
 *
 * Without this, a download failure (a 401 from a gated HuggingFace repo, a checksum mismatch, a
 * dropped connection) was only ever visible as an inline error chip on the AI Config screen —
 * nothing else in the app recorded it, so it never showed up in the Debug log alongside every
 * other AI/service call, even though it's exactly the kind of failure that log exists to surface.
 */
fun interface ModelDownloadDiagnostics {
    fun record(
        model: LocalLlmModel,
        success: Boolean,
        message: String?,
        stackTraceText: String?,
        durationMs: Long,
    )
}
