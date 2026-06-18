package com.twobits.pricedrop.data.remote

/** Parses display price strings like "$49.99", "1,299.00 USD" into a Double, or null. */
object PriceParser {
    private val NUMBER = Regex("""[0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?""")

    fun parse(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val match = NUMBER.find(raw) ?: return null
        return match.value.replace(",", "").toDoubleOrNull()
    }
}
