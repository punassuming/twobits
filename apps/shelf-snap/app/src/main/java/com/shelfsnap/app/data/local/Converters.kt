package com.shelfsnap.app.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.data.model.MarketResearch
import com.shelfsnap.app.data.model.PlatformListing

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }

    @TypeConverter
    fun fromCondition(condition: Condition): String = condition.name

    @TypeConverter
    fun toCondition(value: String): Condition = Condition.valueOf(value)

    @TypeConverter
    fun fromMarketResearch(value: MarketResearch): String = gson.toJson(value)

    @TypeConverter
    fun toMarketResearch(value: String): MarketResearch =
        runCatching { gson.fromJson(value, MarketResearch::class.java) }.getOrNull()
            ?: MarketResearch()

    @TypeConverter
    fun fromListings(value: List<PlatformListing>): String = gson.toJson(value)

    @TypeConverter
    fun toListings(value: String): List<PlatformListing> {
        val type = object : TypeToken<List<PlatformListing>>() {}.type
        return runCatching { gson.fromJson<List<PlatformListing>>(value, type) }.getOrNull()
            ?: emptyList()
    }
}
