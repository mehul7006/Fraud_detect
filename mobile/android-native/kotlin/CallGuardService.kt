package com.fraudshield.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import java.util.concurrent.Executors

/**
 * 16.6: Android's call-screening hook (needs the "Caller ID & spam" role, Android 10+). Android gives at most 5 s;
 * the decision is local and offline (CallGuardEngine) and takes microseconds - contact / call-log lookups are a few ms.
 * Anything that goes wrong lets the call through (fail open: never lose a real call).
 * Privacy: the number is only used on the phone; logcat gets counters and the country code only.
 */
class CallGuardService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val t0 = SystemClock.elapsedRealtime()
        if (Build.VERSION.SDK_INT >= 29 && details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build()); return
        }
        worker.execute {
            var responded = false
            try {
                val ctx = applicationContext
                val hidden = details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED
                val raw = if (hidden) null else details.handle?.schemeSpecificPart
                val lists = CallGuardStore.lists(ctx)
                val canonical = CallNumbers.canonical(raw)
                val contact = if (raw != null) contactOf(ctx, raw) else null
                val recent = canonical != null && recentlyCalled(ctx, canonical)
                val d = CallGuardEngine.decide(CallGuardEngine.Input(raw, contact != null, contact?.second == true, recent), lists)
                val b = CallResponse.Builder()
                when (d.action) {
                    CallGuardEngine.Action.REJECT -> b.setDisallowCall(true).setRejectCall(true).setSkipNotification(true).setSkipCallLog(false)
                    CallGuardEngine.Action.SILENCE -> if (Build.VERSION.SDK_INT >= 29) b.setSilenceCall(true)
                    CallGuardEngine.Action.ALLOW -> Unit
                }
                respondToCall(details, b.build()); responded = true
                val ms = SystemClock.elapsedRealtime() - t0
                android.util.Log.i("FraudShieldCall", "screened kind=${d.kind} action=${d.action} mode=${lists.mode.n} ms=$ms from=${CallNumbers.redacted(raw)}")
                if (d.kind == CallGuardEngine.Kind.KNOWN || d.kind == CallGuardEngine.Kind.SERVICE_CODE) return@execute
                val hi = CallNotifier.hindi()
                val system = details.callerDisplayName?.takeIf { details.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED }
                val label = CallerLabeler.label(d, contact?.first, system, lists.rules, raw, hi)
                try { CallLogStore.get(ctx).add(canonical ?: raw ?: if (hi) "छिपा हुआ" else "hidden", d, label) } catch (e: Exception) { }
                if (d.notify) CallNotifier.show(ctx, canonical ?: raw, d, label)
                try { CallGuardStore.syncIfStale(ctx) } catch (e: Exception) { }
            } catch (e: Throwable) {
                android.util.Log.w("FraudShieldCall", "screening failed: ${e.javaClass.simpleName} - call allowed")
                if (!responded) try { respondToCall(details, CallResponse.Builder().build()) } catch (x: Exception) { }
            }
        }
    }

    companion object {
        private val worker = Executors.newSingleThreadExecutor()

        private fun granted(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

        /** (display name, starred) of a saved contact, else null. Needs READ_CONTACTS (optional). */
        fun contactOf(ctx: Context, raw: String): Pair<String, Boolean>? {
            if (!granted(ctx, Manifest.permission.READ_CONTACTS)) return null
            return try {
                ctx.contentResolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(raw)),
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.STARRED), null, null, null)?.use { c ->
                    if (c.moveToFirst()) (c.getString(0) ?: "") to (c.getInt(1) == 1) else null
                }
            } catch (e: Exception) { null }
        }

        /** The user called this number in the last 30 days. Needs READ_CALL_LOG (optional). */
        fun recentlyCalled(ctx: Context, canonical: String): Boolean {
            if (!granted(ctx, Manifest.permission.READ_CALL_LOG)) return false
            return try {
                val since = System.currentTimeMillis() - 30L * 86_400_000
                ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER),
                    "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ?",
                    arrayOf(CallLog.Calls.OUTGOING_TYPE.toString(), since.toString()), "${CallLog.Calls.DATE} DESC")?.use { c ->
                    var n = 0
                    while (c.moveToNext() && n++ < 500) if (CallNumbers.canonical(c.getString(0)) == canonical) return true
                    false
                } ?: false
            } catch (e: Exception) { false }
        }
    }
}
