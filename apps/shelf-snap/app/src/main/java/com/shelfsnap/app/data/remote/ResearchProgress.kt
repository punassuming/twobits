package com.shelfsnap.app.data.remote

/**
 * Live status emitted during [PriceResearchService.research] so the UI can show what's actually
 * happening instead of an opaque spinner. Each emission is a full snapshot, not a delta — a
 * collector can just replace its state with the latest value, which matters because search
 * queries run concurrently and can emit out of any particular order.
 */
data class ResearchProgress(
    val phase: Phase,
    /** Short human-readable line describing the most recent thing that happened. */
    val detail: String,
    val queriesRun: Int = 0,
    val resultsFound: Int = 0,
    val pagesConfirmed: Int = 0,
    val pagesTarget: Int = 0,
) {
    enum class Phase { SEARCHING, VERIFYING, SYNTHESIZING }
}
