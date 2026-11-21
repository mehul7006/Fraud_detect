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

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        // noise: ongoing (music, downloads, navigation) and group summaries repeat the same text
        if (sbn.isOngoing || (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // 1. count notifications in the last hour
        val key = "n_$pkg"
        val kept = (prefs.getString(key, "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
            .filter { now - it < HOUR } + now
        prefs.edit().putString(key, kept.joinToString(",")).apply()

        val extras = sbn.notification.extras
        val text = listOfNotNull(extras.getCharSequence("android.title"), extras.getCharSequence("android.text"),
            extras.getCharSequence("android.bigText")).joinToString(" ")

        // 2. URLs in any notification (WhatsApp, Telegram, SMS, mail...)
        UrlExtractor.extract(text).take(5).filter { firstSeenRecently(it, now) }
            .forEach { addEvent(prefs, JSONObject().put("kind", "LINK").put("pkg", pkg).put("url", it)) }

        // 3. login-code notifications for WhatsApp/Telegram -> possible takeover attempt
        val low = text.lowercase()
        val mentionsApp = low.contains("whatsapp") || low.contains("telegram") || pkg.contains("whatsapp") || pkg.contains("telegram")
        if (mentionsApp && (low.contains("code") || low.contains("otp"))) {
            val app = if (low.contains("telegram") || pkg.contains("telegram")) "telegram" else "whatsapp"
            addEvent(prefs, JSONObject().put("kind", "LOGIN_CODE").put("pkg", pkg).put("app", app))
        }
        // 4. new device / linked device notices
        if (mentionsApp && (low.contains("new login") || low.contains("new device") || low.contains("linked device")
                    || low.contains("whatsapp web") || low.contains("log in from"))) {
            val app = if (low.contains("telegram") || pkg.contains("telegram")) "telegram" else "whatsapp"
            addEvent(prefs, JSONObject().put("kind", "NEW_DEVICE").put("pkg", pkg).put("app", app))
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
        private const val HOUR = 3_600_000L
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

        fun notifPerHour(ctx: Context, pkg: String): Int {
            val now = System.currentTimeMillis()
            return (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("n_$pkg", "") ?: "")
                .split(",").mapNotNull { it.toLongOrNull() }.count { now - it < HOUR }
        }

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
