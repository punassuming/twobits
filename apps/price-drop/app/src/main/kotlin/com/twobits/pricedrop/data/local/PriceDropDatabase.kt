package com.twobits.pricedrop.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.twobits.pricedrop.data.model.Activity
import com.twobits.pricedrop.data.model.ChatMessageEntity
import com.twobits.pricedrop.data.model.Coupon
import com.twobits.pricedrop.data.model.Drop
import com.twobits.pricedrop.data.model.Offer
import com.twobits.pricedrop.data.model.PriceEvent
import com.twobits.pricedrop.data.model.PriceObservationEntity
import com.twobits.pricedrop.data.model.WatchedProduct

@Database(
    entities = [
        WatchedProduct::class,
        PriceEvent::class,
        PriceObservationEntity::class,
        Drop::class,
        Offer::class,
        Coupon::class,
        Activity::class,
        ChatMessageEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class PriceDropDatabase : RoomDatabase() {
    abstract fun watchedProductDao(): WatchedProductDao

    abstract fun priceEventDao(): PriceEventDao

    abstract fun priceObservationDao(): PriceObservationDao

    abstract fun dropDao(): DropDao

    abstract fun offerDao(): OfferDao

    abstract fun couponDao(): CouponDao

    abstract fun activityDao(): ActivityDao

    abstract fun chatMessageDao(): ChatMessageDao
}
