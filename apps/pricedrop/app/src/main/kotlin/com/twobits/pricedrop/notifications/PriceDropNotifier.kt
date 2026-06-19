package com.twobits.pricedrop.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.twobits.pricedrop.MainActivity
import com.twobits.pricedrop.R
import com.twobits.pricedrop.data.model.Drop
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns PriceDrop's notification channels and posts drop alerts. Notifications are
 * skipped silently when POST_NOTIFICATIONS has not been granted (Android 13+).
 */
@Singleton
class PriceDropNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val fmt = NumberFormat.getCurrencyInstance(Locale.US)

        /** Create the notification channels. Safe to call repeatedly. */
        fun ensureChannels() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DROPS, "Price drops", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Alerts when a watched product hits its target or drops sharply."
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_COUPONS, "Coupons", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New coupons found for watched products."
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ERRORS, "Provider issues", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Background price checks that could not complete."
                },
            )
        }

        fun notifyDrop(drop: Drop, productTitle: String) {
            if (!canPost()) return
            val isCoupon = drop.couponCode.isNotBlank()
            val channel = if (isCoupon) CHANNEL_COUPONS else CHANNEL_DROPS
            val text =
                when {
                    isCoupon -> "Coupon ${drop.couponCode} available"
                    drop.newPrice != null -> "Now ${fmt.format(drop.newPrice)}"
                    else -> "Price update"
                }
            val notification =
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(productTitle.ifBlank { "PriceDrop" })
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(openProductIntent(drop.productId))
                    .build()
            NotificationManagerCompat.from(context).notify(notificationId(drop), notification)
        }

        private fun openProductIntent(productId: Long): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_PRODUCT_ID, productId)
                }
            return PendingIntent.getActivity(
                context,
                productId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun canPost(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        private fun notificationId(drop: Drop): Int = (drop.productId.toInt() * 31) + drop.type.hashCode()

        companion object {
            const val CHANNEL_DROPS = "price_drops"
            const val CHANNEL_COUPONS = "coupons"
            const val CHANNEL_ERRORS = "provider_errors"
            const val EXTRA_PRODUCT_ID = "product_id"
        }
    }
