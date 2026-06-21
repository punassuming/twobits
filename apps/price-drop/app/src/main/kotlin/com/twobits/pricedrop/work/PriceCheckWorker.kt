package com.twobits.pricedrop.work

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.twobits.pricedrop.data.model.Drop
import com.twobits.pricedrop.data.repository.DropsRepository
import com.twobits.pricedrop.data.repository.WatchlistRepository
import com.twobits.pricedrop.data.settings.SettingsPrefs
import com.twobits.pricedrop.notifications.PriceDropNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Periodic background price check. Uses a Hilt [EntryPoint] (rather than `@HiltWorker`)
 * so the app does not need a custom WorkerFactory or the AndroidX Hilt compiler.
 */
class PriceCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun watchlistRepository(): WatchlistRepository

        fun dropsRepository(): DropsRepository

        fun notifier(): PriceDropNotifier

        fun dataStore(): DataStore<Preferences>
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        return runCatching {
            val prefs = deps.dataStore().data.first()
            val quietNow = (prefs[SettingsPrefs.QUIET_HOURS] ?: false) && isQuietTime()

            val products =
                deps
                    .watchlistRepository()
                    .observeAll()
                    .first()
                    .filter { it.isActive }
            for (product in products) {
                val before = product.currentPrice
                runCatching { deps.watchlistRepository().refreshPrice(product.id) }
                runCatching {
                    val query = product.title.ifBlank { product.brand }
                    if (query.isNotBlank()) deps.watchlistRepository().fetchCoupons(product.id, query)
                }
                val after = deps.watchlistRepository().getById(product.id) ?: continue
                val drop =
                    evaluate(
                        before = before,
                        after = after.currentPrice,
                        target = after.targetPrice,
                        thresholdPct = after.alertThresholdPct,
                        productId = product.id,
                        retailer = after.seller,
                    )
                if (drop != null) {
                    val id = deps.dropsRepository().addDrop(drop)
                    if (!quietNow) deps.notifier().notifyDrop(drop.copy(id = id), after.title)
                }
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun evaluate(
        before: Double,
        after: Double,
        target: Double?,
        thresholdPct: Int,
        productId: Long,
        retailer: String,
    ): Drop? {
        val type =
            when {
                target != null && before > target && after <= target -> "target_hit"
                before > 0.0 && after < before && (before - after) / before * 100.0 >= thresholdPct -> "big_drop"
                else -> return null
            }
        return Drop(
            productId = productId,
            type = type,
            oldPrice = before,
            newPrice = after,
            retailer = retailer,
        )
    }

    private fun isQuietTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= SettingsPrefs.QUIET_START_HOUR || hour < SettingsPrefs.QUIET_END_HOUR
    }
}
