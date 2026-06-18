package com.twobits.pricedrop.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.twobits.pricedrop.data.model.Drop
import com.twobits.pricedrop.data.model.PriceEvent
import com.twobits.pricedrop.data.model.WatchedProduct

@Database(
    entities = [WatchedProduct::class, PriceEvent::class, Drop::class],
    version = 1,
    exportSchema = false,
)
abstract class PriceDropDatabase : RoomDatabase() {
    abstract fun watchedProductDao(): WatchedProductDao
    abstract fun priceEventDao(): PriceEventDao
    abstract fun dropDao(): DropDao
}
