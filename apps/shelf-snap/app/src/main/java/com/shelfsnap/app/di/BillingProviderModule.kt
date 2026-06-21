package com.shelfsnap.app.di

import android.content.Context
import com.twobits.billing.BillingConfig
import com.twobits.billing.BillingManager
import com.twobits.billing.SubscriptionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BillingProviderModule {
    @Provides
    @Singleton
    fun provideBillingConfig(): BillingConfig =
        BillingConfig(
            revenueCatPublicKey = "YOUR_REVENUECAT_PUBLIC_KEY",
        )

    @Provides
    @Singleton
    fun provideBillingManager(
        @ApplicationContext context: Context,
        config: BillingConfig,
    ): BillingManager = BillingManager(context, config)

    @Provides
    @Singleton
    fun provideSubscriptionRepository(billingManager: BillingManager): SubscriptionRepository = SubscriptionRepository(billingManager)
}
