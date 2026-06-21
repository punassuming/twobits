package com.twobits.pricedrop.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Activity-timeline event types for a watched product. */
enum class ActivityType(
    val value: String,
) {
    ADDED("added"),
    CHECKED("checked"),
    DROPPED("dropped"),
    COUPON_FOUND("coupon_found"),
    ALERT_SENT("alert_sent"),
    OPENED("opened"),
    ;

    companion object {
        fun fromValue(v: String): ActivityType = entries.firstOrNull { it.value == v } ?: CHECKED
    }
}

@Entity(tableName = "activities")
data class Activity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val type: String,
    val detail: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)
