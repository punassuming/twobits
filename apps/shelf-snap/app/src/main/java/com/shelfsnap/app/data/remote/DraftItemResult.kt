package com.shelfsnap.app.data.remote

import com.shelfsnap.app.data.model.Condition

/** Result returned by the vision analysis service for a set of item photos. */
data class DraftItemResult(
    val category: String = "",
    val brand: String = "",
    val model: String = "",
    /** AI-composed marketable listing title; empty when brand/model couldn't be identified. */
    val title: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val condition: Condition = Condition.GOOD,
    /** Estimated resale/donation value in USD. Always presented as an estimate in the UI. */
    val estimatedValue: Double = 0.0,
    /** 0-100 confidence score from the model. */
    val confidencePercent: Int = 0,
    val error: String? = null,
    /**
     * The [com.twobits.core.pro.ExecutionMode] name that produced this result, when known — set
     * by [com.shelfsnap.app.data.repository.ItemRepository.analysePhotos] so callers can persist
     * it on [com.shelfsnap.app.data.model.Item] for "Processed on your device" badging. Null for
     * results built without going through that call (e.g. a manually-saved draft).
     */
    val executionMode: String? = null,
)
