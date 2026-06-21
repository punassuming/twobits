package com.twobits.pricedrop.notifications

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/** Copies a coupon code to the clipboard when the "Copy CODE" notification action is tapped. */
class CouponCopyReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val code = intent.getStringExtra(EXTRA_CODE) ?: return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("coupon", code))
    }

    companion object {
        const val ACTION_COPY = "com.twobits.pricedrop.ACTION_COPY_COUPON"
        const val EXTRA_CODE = "coupon_code"
    }
}
