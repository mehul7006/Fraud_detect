package com.fraudshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * 7.10: critical threats -> high-priority heads-up notification, plus a full-screen alert where Android allows it.
 * Android 14+: full-screen intents are only granted by default to calling / alarm apps, so we check
 * [canUseFullScreen] and the app shows a card that opens the exact Settings page. Without it the user still gets the
 * heads-up notification (never nothing).
 * Lock screen: only "FraudShield: critical security alert" is shown; the details (e.g. the dangerous link) appear
 * after unlocking.
 */
object AlertNotifier {
    private const val CH = "fraudshield_critical"

    fun canUseFullScreen(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canUseFullScreenIntent()
    }

    /** Android 14+: the "Full-screen notifications" page for this app. */
    fun openFullScreenSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT < 34) return
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun show(ctx: Context, title: String, body: String, id: Int) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Critical security alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE })
        }
        val open = PendingIntent.getActivity(ctx, id,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // what a locked phone shows: no link, no number, no app detail
        val public = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("FraudShield: critical security alert")
            .setContentText("Unlock your phone to see it.")
            .build()
        val b = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setContentIntent(open).setAutoCancel(true)
        if (canUseFullScreen(ctx)) b.setFullScreenIntent(open, true)
        nm.notify(id, b.build())
    }
}
