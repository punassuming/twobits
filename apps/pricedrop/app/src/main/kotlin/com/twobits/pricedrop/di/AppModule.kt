package com.twobits.pricedrop.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.twobits.pricedrop.data.local.DropDao
import com.twobits.pricedrop.data.local.PriceDropDatabase
import com.twobits.pricedrop.data.local.PriceEventDao
import com.twobits.pricedrop.data.local.WatchedProductDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): PriceDropDatabase =
        Room.databaseBuilder(ctx, PriceDropDatabase::class.java, "pricedrop.db").build()

    @Provides
    fun provideWatchedProductDao(db: PriceDropDatabase): WatchedProductDao =
        db.watchedProductDao()

    @Provides
    fun providePriceEventDao(db: PriceDropDatabase): PriceEventDao = db.priceEventDao()

    @Provides
    fun provideDropDao(db: PriceDropDatabase): DropDao = db.dropDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.dataStore
}
