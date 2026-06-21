package com.twobits.pricedrop.data.model

enum class AlertType(val value: String) {
    BELOW_TARGET("below_target"),
    ANY_DROP("any_drop"),
    PERCENT_DROP("percent_drop"),
}
