package com.fraudshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** High-priority heads-up + full-screen-intent notification for critical threats. */
object AlertNotifier {
    private const val CH = "fraudshield_critical"

    fun show(ctx: Context, title: String, body: String, id: Int) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Critical security alerts", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(ctx, id,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(open, true).setContentIntent(open).setAutoCancel(true).build()
        nm.notify(id, n)
    }
}
