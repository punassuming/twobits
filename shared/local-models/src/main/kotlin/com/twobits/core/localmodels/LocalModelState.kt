package com.twobits.core.localmodels

/**
 * Unified install state of a local model, shared across apps. Neutral names cover both Scrybe's
 * download+extract flow and Shelf Snap's SAF import flow:
 *  - [Absent]    — not downloaded / not imported yet.
 *  - [Acquiring] — downloading (and extracting) OR importing; [progressPercent] is 0..100.
 *  - [Ready]     — installed and usable; [path] is the on-device location.
 *  - [Error]     — the last acquisition attempt failed.
 *
 * Apps map this onto the design-layer `LocalModelStatus` at their UI boundary (the two layers are
 * in separate, non-dependent modules, so the mapper lives in each app's AI-config screen).
 */
sealed interface LocalModelState {
    data object Absent : LocalModelState

    data class Acquiring(
        val progressPercent: Int,
    ) : LocalModelState

    data class Ready(
        val path: String,
    ) : LocalModelState

    data class Error(
        val message: String,
    ) : LocalModelState
}
