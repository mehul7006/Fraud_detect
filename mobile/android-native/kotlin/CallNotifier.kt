package com.fraudshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * 16.9: one notification per screened call: "Unknown number - reported as fraud by many people - call rejected" with
 * Block number / Not spam / Report. Rejected / silenced calls use a high-importance channel, plain labels a quiet one.
 * Lock screen shows only "FraudShield call guard"; the number appears after unlocking.
 * Android 13+: without POST_NOTIFICATIONS nothing is shown (the app asks for it on the Call guard screen); the call is
 * still screened and logged.
 */
object CallNotifier {
    private const val CH_ACT = "fraudshield_calls_blocked"
    private const val CH_INFO = "fraudshield_calls_info"
    const val ACTION_BLOCK = "com.fraudshield.app.CALL_BLOCK"
    const val ACTION_NOT_SPAM = "com.fraudshield.app.CALL_NOT_SPAM"
    const val ACTION_REPORT = "com.fraudshield.app.CALL_REPORT"
    const val EXTRA_NUMBER = "number"
    const val EXTRA_ID = "notif_id"

    fun hindi() = Locale.getDefault().language == "hi"

    private fun channels(nm: NotificationManager, hi: Boolean) {
        if (Build.VERSION.SDK_INT < 26) return
        nm.createNotificationChannel(NotificationChannel(CH_ACT, if (hi) "रोकी गई कॉल" else "Blocked and silenced calls",
            NotificationManager.IMPORTANCE_HIGH).apply { lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE })
        nm.createNotificationChannel(NotificationChannel(CH_INFO, if (hi) "कॉलर जानकारी" else "Caller information",
            NotificationManager.IMPORTANCE_DEFAULT).apply { lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE })
    }

    fun show(ctx: Context, number: String?, d: CallGuardEngine.Decision, label: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val hi = hindi()
        channels(nm, hi)
        val id = 50_000 + ((number ?: "hidden").hashCode() and 0x7fff)
        val acted = d.action != CallGuardEngine.Action.ALLOW
        val open = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_LOG, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(ctx, if (acted) CH_ACT else CH_INFO)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(if (hi) "FraudShield कॉल गार्ड" else "FraudShield call guard")
            .setContentText(CallerLabeler.outcome(d.action, hi)).build()
        val text = listOfNotNull(number, CallerLabeler.outcome(d.action, hi)).joinToString(" · ")
        val b = NotificationCompat.Builder(ctx, if (acted) CH_ACT else CH_INFO)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(label).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(if (acted) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setContentIntent(open).setAutoCancel(true)
        val protectedSeries = d.kind == CallGuardEngine.Kind.PROTECTED_SERIES
        if (number != null && d.canonical != null && !protectedSeries) {
            if (!acted) b.addAction(0, if (hi) "नंबर ब्लॉक करें" else "Block number", action(ctx, ACTION_BLOCK, d.canonical, id))
            if (acted || d.kind == CallGuardEngine.Kind.SCAM_LIST)
                b.addAction(0, if (hi) "स्पैम नहीं" else "Not spam", action(ctx, ACTION_NOT_SPAM, d.canonical, id))
            b.addAction(0, if (hi) "रिपोर्ट करें" else "Report", action(ctx, ACTION_REPORT, d.canonical, id))
        }
        try { nm.notify(id, b.build()) } catch (e: SecurityException) { /* Android 13+ without notification permission */ }
    }

    private fun action(ctx: Context, a: String, number: String, id: Int): PendingIntent =
        PendingIntent.getBroadcast(ctx, (a + number).hashCode(), Intent(ctx, CallActionReceiver::class.java).setAction(a)
            .putExtra(EXTRA_NUMBER, number).putExtra(EXTRA_ID, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun done(ctx: Context, id: Int, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        channels(nm, hindi())
        try {
            nm.notify(id, NotificationCompat.Builder(ctx, CH_INFO).setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("FraudShield").setContentText(text).setAutoCancel(true)
                .setTimeoutAfter(15_000).build())
        } catch (e: SecurityException) { }
    }

    const val EXTRA_OPEN_LOG = "open_call_log"
}

/** Buttons of the call notification. Report is the only path that sends the number to the server (user's tap). */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val number = intent.getStringExtra(CallNotifier.EXTRA_NUMBER) ?: return
        val id = intent.getIntExtra(CallNotifier.EXTRA_ID, 0)
        val hi = CallNotifier.hindi()
        CallGuardStore.load(ctx)
        when (intent.action) {
            CallNotifier.ACTION_BLOCK -> {
                val ok = CallGuardStore.setBlocked(ctx, number, true) != null
                CallNotifier.done(ctx, id, if (!ok) (if (hi) "यह नंबर ब्लॉक नहीं किया जा सकता" else "This number cannot be blocked")
                    else if (hi) "नंबर ब्लॉक किया गया - अगली कॉल अपने आप कटेगी" else "Number blocked - next calls are rejected")
            }
            CallNotifier.ACTION_NOT_SPAM -> {
                CallGuardStore.setAllowed(ctx, number, true)
                Thread { try { CallLogStore.get(ctx).markUndone(number) } catch (e: Exception) { } }.start()
                CallNotifier.done(ctx, id, if (hi) "ठीक है - यह नंबर अब हमेशा आने दिया जाएगा" else "OK - this number is always allowed now")
            }
            CallNotifier.ACTION_REPORT -> {
                val pending = goAsync()
                Thread {
                    val code = try { CallGuardStore.report(ctx, number, "SCAM") } catch (e: Exception) { 0 }
                    android.util.Log.i("FraudShieldCall", "report sent status=$code")
                    CallNotifier.done(ctx, id, when (code) {
                        in 200..299 -> if (hi) "रिपोर्ट भेजी गई, धन्यवाद" else "Reported - thank you"
                        422 -> if (hi) "यह आधिकारिक सेवा नंबर है - रिपोर्ट नहीं किया जा सकता" else "Official service number - cannot be reported"
                        else -> if (hi) "रिपोर्ट नहीं भेजी जा सकी - ऐप खोलकर फिर कोशिश करें" else "Could not report now - try again from the app"
                    })
                    pending.finish()
                }.start()
            }
        }
    }
}
