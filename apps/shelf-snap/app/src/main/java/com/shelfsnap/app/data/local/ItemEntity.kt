package com.shelfsnap.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.shelfsnap.app.data.model.Condition
import com.shelfsnap.app.data.model.Item
import com.shelfsnap.app.data.model.MarketResearch
import com.shelfsnap.app.data.model.PlatformListing

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val photoPaths: List<String> = emptyList(),
    val category: String = "",
    val description: String = "",
    val condition: Condition = Condition.GOOD,
    val estimatedValue: Double = 0.0,
    val confidencePercent: Int = 0,
    val isDraft: Boolean = true,
    // v2 extended attributes
    val brand: String = "",
    val model: String = "",
    val size: String = "",
    val color: String = "",
    val quantity: Int = 1,
    val originalPrice: Double = 0.0,
    val tags: List<String> = emptyList(),
    // v2 market research + listings (persisted as JSON via Converters)
    val marketResearch: MarketResearch = MarketResearch(),
    val listings: List<PlatformListing> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val primaryPhotoIndex: Int = 0
)

fun ItemEntity.toDomain() = Item(
    id = id,
    photoPaths = photoPaths,
    category = category,
    description = description,
    condition = condition,
    estimatedValue = estimatedValue,
    confidencePercent = confidencePercent,
    isDraft = isDraft,
    brand = brand,
    model = model,
    size = size,
    color = color,
    quantity = quantity,
    originalPrice = originalPrice,
    tags = tags,
    marketResearch = marketResearch,
    listings = listings,
    createdAt = createdAt,
    updatedAt = updatedAt,
    primaryPhotoIndex = primaryPhotoIndex
)

fun Item.toEntity() = ItemEntity(
    id = id,
    photoPaths = photoPaths,
    category = category,
    description = description,
    condition = condition,
    estimatedValue = estimatedValue,
    confidencePercent = confidencePercent,
    isDraft = isDraft,
    brand = brand,
    model = model,
    size = size,
    color = color,
    quantity = quantity,
    originalPrice = originalPrice,
    tags = tags,
    marketResearch = marketResearch,
    listings = listings,
    createdAt = createdAt,
    updatedAt = updatedAt,
    primaryPhotoIndex = primaryPhotoIndex
)
