package com.twobits.pricedrop.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.twobits.pricedrop.MainActivity
import com.twobits.pricedrop.R
import com.twobits.pricedrop.data.model.Drop
import com.twobits.pricedrop.work.ModelDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns PriceDrop's notification channels and posts drop alerts. Each drop type
 * gets a type-specific accent color and title matching the notification shade mockup.
 * Notifications are skipped silently when POST_NOTIFICATIONS has not been granted (Android 13+).
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
            manager.createNotificationChannel(
                NotificationChannel(
                    ModelDownloadWorker.CHANNEL_ID,
                    "Model downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Progress for on-device AI model downloads."
                },
            )
        }

        fun notifyDrop(
            drop: Drop,
            productTitle: String,
        ) {
            if (!canPost()) return
            val isCoupon = drop.type == TYPE_COUPON_FOUND || drop.couponCode.isNotBlank()
            val isError = drop.type == TYPE_PROVIDER_ERROR
            val channel =
                when {
                    isError -> CHANNEL_ERRORS
                    isCoupon -> CHANNEL_COUPONS
                    else -> CHANNEL_DROPS
                }
            val accentColor = accentFor(drop.type, isCoupon)
            val title = titleFor(drop.type, isCoupon)
            val body = bodyFor(drop, productTitle)
            val sub = subTextFor(drop)

            val builder =
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setColor(accentColor)
                    .setAutoCancel(true)
                    .setContentIntent(openProductIntent(drop.productId))
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(body)
                            .setSummaryText(sub),
                    )
                    .addAction(0, "Open item", openProductIntent(drop.productId))

            if (isCoupon && drop.couponCode.isNotBlank()) {
                builder.addAction(0, "Copy ${drop.couponCode}", copyCodeIntent(drop.couponCode))
            }

            NotificationManagerCompat.from(context).notify(notificationId(drop), builder.build())
        }

        private fun accentFor(
            type: String,
            isCoupon: Boolean,
        ): Int =
            when {
                type == TYPE_TARGET_HIT -> Color.parseColor("#88D7A8")
                isCoupon || type == TYPE_COUPON_FOUND -> Color.parseColor("#FFD580")
                type == TYPE_BIG_DROP -> Color.parseColor("#FF8066")
                type == TYPE_PROVIDER_ERROR -> Color.parseColor("#FF8A80")
                else -> Color.parseColor("#FF8066")
            }

        private fun titleFor(
            type: String,
            isCoupon: Boolean,
        ): String =
            when {
                type == TYPE_TARGET_HIT -> "Below target price"
                isCoupon || type == TYPE_COUPON_FOUND -> "Coupon may apply"
                type == TYPE_BIG_DROP -> "Big price drop"
                type == TYPE_PROVIDER_ERROR -> "Provider needs attention"
                else -> "PriceDrop alert"
            }

        private fun bodyFor(
            drop: Drop,
            productTitle: String,
        ): String {
            val name = productTitle.ifBlank { "Item" }
            return when {
                drop.type == TYPE_TARGET_HIT && drop.newPrice != null && drop.oldPrice != null -> {
                    val savings = fmt.format(drop.oldPrice - drop.newPrice)
                    "$name is now ${fmt.format(drop.newPrice)} — $savings below your target"
                }
                drop.type == TYPE_BIG_DROP && drop.newPrice != null && drop.oldPrice != null && drop.oldPrice > 0.0 -> {
                    val pct = ((drop.oldPrice - drop.newPrice) / drop.oldPrice * 100.0).toInt()
                    "$name dropped $pct% — now ${fmt.format(drop.newPrice)}"
                }
                drop.couponCode.isNotBlank() -> "${drop.couponCode} found for $name"
                drop.type == TYPE_PROVIDER_ERROR ->
                    drop.retailer.ifBlank { "A provider failed. Check Settings → Providers." }
                else -> name
            }
        }

        private fun subTextFor(drop: Drop): String =
            when {
                drop.type == TYPE_TARGET_HIT -> "Tap to view deal"
                drop.type == TYPE_BIG_DROP && drop.oldPrice != null -> "Down from ${fmt.format(drop.oldPrice)}"
                drop.couponCode.isNotBlank() -> "Unverified · test before relying on it"
                drop.type == TYPE_PROVIDER_ERROR -> "Check Settings → Providers"
                else -> ""
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

        private fun copyCodeIntent(code: String): PendingIntent {
            val intent =
                Intent(context, CouponCopyReceiver::class.java).apply {
                    action = CouponCopyReceiver.ACTION_COPY
                    putExtra(CouponCopyReceiver.EXTRA_CODE, code)
                }
            return PendingIntent.getBroadcast(
                context,
                code.hashCode(),
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

            const val TYPE_TARGET_HIT = "target_hit"
            const val TYPE_BIG_DROP = "big_drop"
            const val TYPE_COUPON_FOUND = "coupon_found"
            const val TYPE_PROVIDER_ERROR = "provider_error"
        }
    }
