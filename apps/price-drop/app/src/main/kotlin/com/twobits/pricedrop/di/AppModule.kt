package com.twobits.pricedrop.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.twobits.pricedrop.BuildConfig
import com.twobits.pricedrop.data.local.ActivityDao
import com.twobits.pricedrop.data.local.ChatMessageDao
import com.twobits.pricedrop.data.local.CouponDao
import com.twobits.pricedrop.data.local.DropDao
import com.twobits.pricedrop.data.local.OfferDao
import com.twobits.pricedrop.data.local.PriceDropDatabase
import com.twobits.pricedrop.data.local.PriceEventDao
import com.twobits.pricedrop.data.local.PriceObservationDao
import com.twobits.pricedrop.data.local.WatchedProductDao
import com.twobits.pricedrop.data.provider.registry.DefaultProviderRegistry
import com.twobits.pricedrop.data.provider.registry.ProviderRegistry
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
    fun provideProviderRegistry(registry: DefaultProviderRegistry): ProviderRegistry = registry

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext ctx: Context,
    ): PriceDropDatabase =
        Room
            .databaseBuilder(ctx, PriceDropDatabase::class.java, "pricedrop.db")
            .apply { if (BuildConfig.DEBUG) enableMultiInstanceInvalidation() }
            .addMigrations(MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideWatchedProductDao(db: PriceDropDatabase): WatchedProductDao = db.watchedProductDao()

    @Provides
    fun providePriceEventDao(db: PriceDropDatabase): PriceEventDao = db.priceEventDao()

    @Provides
    fun providePriceObservationDao(db: PriceDropDatabase): PriceObservationDao = db.priceObservationDao()

    @Provides
    fun provideDropDao(db: PriceDropDatabase): DropDao = db.dropDao()

    @Provides
    fun provideOfferDao(db: PriceDropDatabase): OfferDao = db.offerDao()

    @Provides
    fun provideCouponDao(db: PriceDropDatabase): CouponDao = db.couponDao()

    @Provides
    fun provideActivityDao(db: PriceDropDatabase): ActivityDao = db.activityDao()

    @Provides
    fun provideChatMessageDao(db: PriceDropDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext ctx: Context,
    ): DataStore<Preferences> = ctx.dataStore

    private val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watched_products ADD COLUMN canonicalProductId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watched_products ADD COLUMN model TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watched_products ADD COLUMN manufacturerPartNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watched_products ADD COLUMN gtin TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watched_products ADD COLUMN ean TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE watched_products SET canonicalProductId = 'legacy:' || id WHERE canonicalProductId = ''")
                db.execSQL("ALTER TABLE coupons ADD COLUMN applicability TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE coupons ADD COLUMN confidence REAL NOT NULL DEFAULT 0.0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS price_observations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        productId INTEGER NOT NULL,
                        canonicalProductId TEXT NOT NULL,
                        merchantId TEXT NOT NULL,
                        listingId TEXT,
                        itemPriceMinor INTEGER NOT NULL,
                        shippingPriceMinor INTEGER,
                        totalPriceMinor INTEGER NOT NULL,
                        currency TEXT NOT NULL,
                        availability TEXT NOT NULL,
                        observedAt INTEGER NOT NULL,
                        provider TEXT NOT NULL,
                        provenance TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_observations_productId_observedAt ON price_observations(productId, observedAt)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_price_observations_canonicalProductId_merchantId_listingId_observedAt " +
                        "ON price_observations(canonicalProductId, merchantId, listingId, observedAt)",
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO price_observations (
                        productId, canonicalProductId, merchantId, listingId,
                        itemPriceMinor, shippingPriceMinor, totalPriceMinor, currency,
                        availability, observedAt, provider, provenance
                    )
                    SELECT
                        productId, 'legacy:' || productId,
                        CASE WHEN retailer = '' THEN 'unknown' ELSE retailer END,
                        'observation:' || recordedAt, CAST(ROUND(price * 100) AS INTEGER), NULL,
                        CAST(ROUND(effectivePrice * 100) AS INTEGER), 'USD', 'UNKNOWN',
                        recordedAt, 'legacy', 'migration_v4'
                    FROM price_events
                    """.trimIndent(),
                )
            }
        }
}
