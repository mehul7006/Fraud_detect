package com.fraudshield.app

import android.Manifest
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 15.2 live-scam signals + 15.4 OTP-share guard (Android side of [ScamSignalLogic]).
 *
 * Host: the notification listener (always running once Notification Access is on) calls [ensure]; the user's opt-in
 * switch ("Live scam guard") calls [setEnabled]. Needs READ_PHONE_STATE (call state), READ_CALL_LOG (the ringing number,
 * to check it against contacts ON THE PHONE), READ_CONTACTS and Usage Access (which app is in front). Each missing
 * permission only weakens the guard; nothing crashes.
 *
 * PRIVACY: the caller's number is looked up in contacts and dropped; app names are matched against two lists and
 * dropped; the one-time code is never read out of the text. Only signal kinds + call length go to the server, and
 * only while an unknown-number call is going on (or ended < 10 min ago).
 */
object ScamSignalCollector {
    private const val PREFS = "fraudshield_scamguard"
    private const val ALERT_ID = 0x5CA
    private const val OTP_ID = 0x5CB
    private const val POLL_MS = 4_000L

    private val thread by lazy { HandlerThread("fs-scamguard").apply { start() } }
    private val handler by lazy { Handler(thread.looper) }
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var logic = ScamSignalLogic()
    @Volatile private var registered = false
    private var listener: PhoneStateListener? = null
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var ringingNumber: String? = null
    private var lastPoll = 0L
    private var polling = false
    @Volatile var lastLevelFromServer: String? = null; private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun granted(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("enabled", false)
    /** 15.4 is on by default (purely local, nothing leaves the phone); it can be switched off separately. */
    fun otpGuard(ctx: Context) = prefs(ctx).getBoolean("otpGuard", true)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("enabled", on).apply()
        if (on) ensure(ctx) else stop()
    }

    fun setOtpGuard(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("otpGuard", on).apply()

    fun status(ctx: Context): Map<String, Any?> = mapOf(
        "enabled" to enabled(ctx), "otpGuard" to otpGuard(ctx), "running" to registered,
        "phoneState" to granted(ctx, Manifest.permission.READ_PHONE_STATE),
        "callLog" to granted(ctx, Manifest.permission.READ_CALL_LOG),
        "contacts" to granted(ctx, Manifest.permission.READ_CONTACTS),
        "usageAccess" to UsageAuditor.hasUsageAccess(ctx),
        "notificationAccess" to AdNotificationListener.isEnabled(ctx),
        "remoteAccessApps" to remoteAccess(ctx).size)

    /** Starts listening to call state if the user opted in and the phone permission is there. Idempotent. */
    fun ensure(ctx: Context) {
        val app = ctx.applicationContext
        if (!enabled(app) || registered || !granted(app, Manifest.permission.READ_PHONE_STATE)) return
        handler.post {
            if (registered) return@post
            try {
                @Suppress("DEPRECATION")
                val l = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, number: String?) = onCallState(app, state, number)
                }
                val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                @Suppress("DEPRECATION") tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
                listener = l
                registered = true
                android.util.Log.i("FraudShieldGuard", "live scam guard listening")
            } catch (e: Exception) {
                android.util.Log.w("FraudShieldGuard", "cannot listen to call state: ${e.javaClass.simpleName}")
            }
        }
        refreshConfig(app)
    }

    private fun stop() = handler.post {
        listener?.let { l -> try {
            // the context is only needed for the service; any context works
            @Suppress("DEPRECATION") (appCtx?.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
                ?.listen(l, PhoneStateListener.LISTEN_NONE)
        } catch (_: Exception) { } }
        listener = null
        registered = false
        logic = ScamSignalLogic(logic.windowMs, logic.longCallMs)
    }

    @Volatile private var appCtx: Context? = null

    private fun onCallState(ctx: Context, state: Int, number: String?) {
        appCtx = ctx
        val now = System.currentTimeMillis()
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> ringingNumber = number
            TelephonyManager.CALL_STATE_OFFHOOK -> if (lastState != TelephonyManager.CALL_STATE_OFFHOOK) {
                val incoming = lastState == TelephonyManager.CALL_STATE_RINGING
                val unknown = incoming && !isContact(ctx, ringingNumber)
                ringingNumber = null // the number is not kept
                logic.onCallStarted(now, unknown)
                android.util.Log.i("FraudShieldGuard", "call started (incoming=$incoming, unknown=$unknown)")
                if (unknown) startPolling(ctx, now - 60_000L)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) logic.onCallEnded(now)
                ringingNumber = null
            }
        }
        lastState = state
    }

    /** Saved contact? Checked on the phone only. Hidden / unavailable number or no permission = unknown. */
    private fun isContact(ctx: Context, number: String?): Boolean {
        if (number.isNullOrBlank() || !granted(ctx, Manifest.permission.READ_CONTACTS)) return false
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.count > 0 } ?: false
        } catch (_: Exception) { false }
    }

    private fun startPolling(ctx: Context, from: Long) {
        lastPoll = from
        if (polling) return
        polling = true
        handler.post(object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                logic.tick(now)
                foregroundSince(ctx, lastPoll - 1_000L, now).forEach { logic.onForeground(now, it, remoteAccess(ctx), ScamSignalLogic.BANKING) }
                lastPoll = now
                process(ctx, now)
                logic.expire(now)
                if (logic.armed(now) || logic.callActive) handler.postDelayed(this, POLL_MS) else polling = false
            }
        })
    }

    /** Apps that came to the front in [from, to] (Usage Access; empty without it). Only package names, not kept. */
    private fun foregroundSince(ctx: Context, from: Long, to: Long): Set<String> {
        if (!UsageAuditor.hasUsageAccess(ctx)) return emptySet()
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val ev = usm.queryEvents(from, to)
            val e = UsageEvents.Event()
            val out = HashSet<String>()
            val resumed = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_RESUMED else @Suppress("DEPRECATION") UsageEvents.Event.MOVE_TO_FOREGROUND
            while (ev.hasNextEvent()) { ev.getNextEvent(e); if (e.eventType == resumed) out.add(e.packageName) }
            out
        } catch (_: Exception) { emptySet() }
    }

    /** Called by the notification listener for a one-time-code notification. The text/code is NOT passed in. */
    fun onOtp(ctx: Context) {
        val app = ctx.applicationContext
        if (enabled(app) && registered) {
            handler.post {
                val now = System.currentTimeMillis()
                if (logic.onOtp(now) && otpGuard(app)) otpWarning(app)
                process(app, now)
            }
            return
        }
        // guard switched off or not listening: 15.4 still works from the current call state
        if (!otpGuard(app) || !granted(app, Manifest.permission.READ_PHONE_STATE)) return
        val tm = app.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val inCall = try { @Suppress("DEPRECATION") tm.callState != TelephonyManager.CALL_STATE_IDLE } catch (_: SecurityException) { false }
        if (inCall) otpWarning(app)
    }

    private fun hindi() = Locale.getDefault().language == "hi"

    /** 15.4: heads-up warning; the code is not shown, stored or logged. */
    private fun otpWarning(ctx: Context) {
        val (t, b) = if (hindi()) "यह कोड किसी को न बताएँ" to
            "आप कॉल पर हैं। बैंक, पुलिस या कोई भी कंपनी कॉल पर कोड नहीं माँगती। इसे पढ़कर न सुनाएँ और कहीं न डालें।"
        else "Do not read this code out" to
            "You are on a call. Banks, police and companies never ask for a code on a call. Do not read it out or type it anywhere the caller asks."
        AlertNotifier.show(ctx, t, b, OTP_ID)
        android.util.Log.i("FraudShieldGuard", "OTP-share warning shown")
    }

    private fun process(ctx: Context, now: Long) {
        val l = logic
        for (k in l.toSend(now)) {
            l.markSent(k, now)
            val secs = l.callSeconds(now)
            worker.execute { send(ctx, k, secs) }
        }
        l.newAlert(now)?.let { alert(ctx, it) }
    }

    private fun send(ctx: Context, k: ScamSignalLogic.Kind, callSeconds: Int) {
        val body = JSONObject().put("signal", k.name)
        if (k == ScamSignalLogic.Kind.LONG_UNKNOWN_CALL) body.put("unknownCaller", true).put("callSeconds", maxOf(callSeconds, (logic.longCallMs / 1000).toInt()))
        val (code, resp) = BackgroundScanner.request(ctx, "/scam-signals", body)
        if (code !in 200..299 || resp == null) {
            android.util.Log.i("FraudShieldGuard", "signal ${k.name} not sent ($code) - phone decides alone")
            return
        }
        val level = JSONObject(resp).optString("level")
        lastLevelFromServer = level
        android.util.Log.i("FraudShieldGuard", "signal ${k.name} sent, server level $level")
        // the server only knows what this phone sent, so its level normally equals ours; if it is higher, warn now
        if (level == "CRITICAL" && logic.level(System.currentTimeMillis()) != ScamSignalLogic.Level.CRITICAL)
            handler.post { alert(ctx, ScamSignalLogic.Level.CRITICAL) }
    }

    /** CRITICAL / WARNING: full-screen alert + the app's STOP screen (via the event queue the app reads). */
    private fun alert(ctx: Context, level: ScamSignalLogic.Level) {
        val critical = level == ScamSignalLogic.Level.CRITICAL
        val (t, b) = if (hindi()) (if (critical) "रुकिए! यह धोखा हो सकता है" else "सावधान: यह धोखा हो सकता है") to
            "अनजान कॉल, स्क्रीन-शेयरिंग ऐप और बैंक ऐप एक साथ - ठगी ठीक ऐसे ही होती है। कॉल काटें, कोई कोड न बताएँ, स्क्रीन-शेयरिंग ऐप बंद करें।"
        else (if (critical) "STOP - this looks like a scam" else "Careful - this may be a scam") to
            "Unknown caller + screen-sharing app + banking app at the same time is how bank scams work. Hang up, share no code, close the screen-sharing app."
        AlertNotifier.show(ctx, t, b, ALERT_ID)
        AdNotificationListener.queueEvent(ctx, JSONObject().put("kind", "LIVE_SCAM").put("level", level.name))
        android.util.Log.i("FraudShieldGuard", "live scam alert: ${level.name}")
    }

    fun remoteAccess(ctx: Context): Set<String> =
        prefs(ctx).getString("remote", null)?.split(',')?.filter { it.isNotBlank() }?.toSet()?.ifEmpty { null }
            ?: ScamSignalLogic.REMOTE_ACCESS_DEFAULT

    /** GET /scam-signals/config at most once a day (list of screen-sharing apps, window, long-call length). */
    private fun refreshConfig(ctx: Context) {
        val p = prefs(ctx)
        if (System.currentTimeMillis() - p.getLong("configAt", 0) < 86_400_000L) return
        worker.execute {
            val (code, body) = BackgroundScanner.request(ctx, "/scam-signals/config", null)
            if (code != 200 || body == null) return@execute
            val j = JSONObject(body)
            val list = j.optJSONArray("remoteAccessPackages")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: return@execute
            p.edit().putString("remote", list.joinToString(",")).putLong("configAt", System.currentTimeMillis()).apply()
            val window = j.optLong("windowMinutes", 10) * 60_000L
            val long = j.optInt("longCallSeconds", 180) * 1000L
            handler.post { if (!logic.armed(System.currentTimeMillis()) && !logic.callActive) logic = ScamSignalLogic(window, long) }
        }
    }

    /** Debug builds only (device test of the STOP path without a real scam): run the four signals through the logic. */
    fun simulate(ctx: Context) {
        val app = ctx.applicationContext
        handler.post {
            val now = System.currentTimeMillis()
            logic.onCallStarted(now - logic.longCallMs - 1_000, true)
            logic.tick(now)
            logic.onForeground(now, remoteAccess(app).first(), remoteAccess(app), ScamSignalLogic.BANKING)
            logic.onForeground(now, ScamSignalLogic.BANKING.first(), remoteAccess(app), ScamSignalLogic.BANKING)
            logic.onOtp(now)
            process(app, now)
            logic.onCallEnded(now)
        }
    }
}
