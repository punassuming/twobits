package com.shelfsnap.app.data.remote

import com.shelfsnap.app.data.model.Condition

/** Result returned by the vision analysis service for a set of item photos. */
data class DraftItemResult(
    val category: String = "",
    val brand: String = "",
    val model: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val condition: Condition = Condition.GOOD,
    /** Estimated resale/donation value in USD. Always presented as an estimate in the UI. */
    val estimatedValue: Double = 0.0,
    /** 0-100 confidence score from the model. */
    val confidencePercent: Int = 0,
    val error: String? = null
)
