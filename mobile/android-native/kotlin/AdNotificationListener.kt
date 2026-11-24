package com.fraudshield.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runs in background once the user grants Notification Access.
 * PRIVACY: we never store message text. We keep only (a) per-package notification timestamps for ad-abuse counting,
 * (b) extracted URLs, (c) a flag "login-code notification seen" (the code itself is never stored).
 */
class AdNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() { // 13.10 / 13.11: this service runs all the time - a good host
        InstallWatcher.register(this)
        try { WarnScheduler.tick(this) } catch (_: Exception) { }
        try { ScamSignalCollector.ensure(this) } catch (_: Exception) { } // 15.2 live scam guard (opt-in)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        // noise: ongoing (music, downloads, navigation) and group summaries repeat the same text
        if (sbn.isOngoing || (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // 1. count notifications in the last hour (timestamps only, capped per package - 6.4)
        synchronized(LOCK) {
            val key = "n_$pkg"
            prefs.edit().putString(key, NotifCounter.add(prefs.getString(key, null), now)).apply()
        }

        val extras = sbn.notification.extras
        val text = listOfNotNull(extras.getCharSequence("android.title"), extras.getCharSequence("android.text"),
            extras.getCharSequence("android.bigText")).joinToString(" ")

        // 15.2 / 15.4: a one-time code arrived - only the FACT is passed on, never the text or the code
        if (ScamSignalLogic.isOtpText(text) && firstSeenRecently("otp:$pkg:${text.hashCode()}", now)) {
            try { ScamSignalCollector.onOtp(this) } catch (_: Exception) { }
        }

        // 2. URLs in any notification (WhatsApp, Telegram, SMS, mail...)
        // 7.11: while FraudShield is closed the link is checked right here (BackgroundScanner); otherwise / if that
        // is not possible it is queued for the app exactly as before.
        val queue: (JSONObject) -> Unit = { addEvent(prefs, it) }
        UrlExtractor.extract(text).take(5).filter { firstSeenRecently(it, now) }
            .forEach { url -> if (!BackgroundScanner.onLink(this, url, pkg, queue)) queue(JSONObject().put("kind", "LINK").put("pkg", pkg).put("url", url)) }

        // 3. login-code notifications for WhatsApp/Telegram -> possible takeover attempt
        val low = text.lowercase()
        val mentionsApp = low.contains("whatsapp") || low.contains("telegram") || pkg.contains("whatsapp") || pkg.contains("telegram")
        // SMS / chat apps re-post old notifications when their group updates ("3 new messages"): the same notice must
        // not alert again. Key = app + kind + hash of the text, kept in memory only (the code is never stored).
        val noticeKey = { kind: String -> "notice:$kind:$pkg:${text.hashCode()}" }
        if (mentionsApp && (low.contains("code") || low.contains("otp")) && firstSeenRecently(noticeKey("code"), now)) {
            val app = if (low.contains("telegram") || pkg.contains("telegram")) "telegram" else "whatsapp"
            addEvent(prefs, JSONObject().put("kind", "LOGIN_CODE").put("pkg", pkg).put("app", app))
        }
        // 4. new device / linked device notices
        if (mentionsApp && (low.contains("new login") || low.contains("new device") || low.contains("linked device")
                    || low.contains("whatsapp web") || low.contains("log in from")) && firstSeenRecently(noticeKey("device"), now)) {
            val app = if (low.contains("telegram") || pkg.contains("telegram")) "telegram" else "whatsapp"
            if (!BackgroundScanner.onNewDevice(this, app, queue)) addEvent(prefs, JSONObject().put("kind", "NEW_DEVICE").put("pkg", pkg).put("app", app))
        }
    }

    @Synchronized
    private fun addEvent(prefs: android.content.SharedPreferences, e: JSONObject) {
        val arr = JSONArray(prefs.getString(EVENTS, "[]"))
        if (arr.length() > 200) return // safety cap
        arr.put(e)
        prefs.edit().putString(EVENTS, arr.toString()).apply()
    }

    companion object {
        const val PREFS = "fraudshield_events"
        private const val EVENTS = "pending"
        private const val DEDUPE_MS = 300_000L // same link within 5 minutes is queued once
        private val seen = object : LinkedHashMap<String, Long>(64, 0.75f, false) {
            override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Long>) = size > 200
        }

        @Synchronized
        fun firstSeenRecently(url: String, now: Long): Boolean {
            val k = url.lowercase()
            val last = seen[k]
            if (last != null && now - last < DEDUPE_MS) return false
            seen[k] = now
            return true
        }

        private val LOCK = Any()

        /** 15.2: other parts (live scam guard) hand events to the app through the same queue. */
        @Synchronized
        fun queueEvent(ctx: Context, e: JSONObject) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(EVENTS, "[]"))
            if (arr.length() > 200) return
            arr.put(e)
            prefs.edit().putString(EVENTS, arr.toString()).apply()
        }

        fun notifPerHour(ctx: Context, pkg: String): Int =
            NotifCounter.count(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("n_$pkg", null), System.currentTimeMillis())

        /** Returns pending events as list of maps and clears them. */
        @Synchronized
        fun takePending(ctx: Context): List<Map<String, String>> {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(EVENTS, "[]"))
            prefs.edit().putString(EVENTS, "[]").apply()
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.keys().asSequence().associateWith { o.getString(it) }
            }
        }

        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(ctx, AdNotificationListener::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
