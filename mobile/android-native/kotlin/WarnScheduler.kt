package com.fraudshield.app

import android.content.Context
import org.json.JSONObject

/**
 * 13.10: keep reminding about a risky app until it is fixed - at detection, after 1 hour, then once a day.
 * Frequency cap: at most 3 reminders a day across all apps (so people are not spammed). Stops when the app is
 * uninstalled, the user taps Ignore, or its internet is blocked.
 */
object WarnScheduler {
    private const val PREFS = "fraudshield_warn"
    const val MAX_PER_DAY = 3

    data class Entry(val pkg: String, val title: String, val body: String, val firstAt: Long, val lastAt: Long, val count: Int)

    /** Pure schedule rule (unit-tested): when is this entry's next reminder due? */
    fun nextDue(e: Entry): Long = when (e.count) {
        0 -> e.firstAt
        1 -> e.firstAt + 3_600_000L                 // +1 hour
        else -> e.lastAt + 24 * 3_600_000L          // then daily
    }

    /** Pure: which entries to send now, respecting the daily cap ([sentToday] already sent in the last 24 h). */
    fun due(entries: List<Entry>, now: Long, sentToday: Int): List<Entry> =
        entries.filter { nextDue(it) <= now }.sortedBy { nextDue(it) }.take((MAX_PER_DAY - sentToday).coerceAtLeast(0))

    fun add(ctx: Context, pkg: String, title: String, body: String) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.contains("e:$pkg") || p.getBoolean("ignored:$pkg", false)) return
        val now = System.currentTimeMillis()
        p.edit().putString("e:$pkg", JSONObject().put("t", title).put("b", body).put("f", now).put("l", 0L).put("c", 0).toString()).apply()
        tick(ctx)
    }

    /** User chose Ignore (remembered) or fixed it. */
    fun stop(ctx: Context, pkg: String, ignore: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("e:$pkg").apply { if (ignore) putBoolean("ignored:$pkg", true) }.apply()
    }

    fun isIgnored(ctx: Context, pkg: String) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("ignored:$pkg", false)

    /** Sends what is due. Called by the VPN service (every 15 min) and the notification listener. */
    @Synchronized fun tick(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val pm = ctx.packageManager
        val blocked = AppFirewall.blocked(ctx)
        val entries = p.all.filterKeys { it.startsWith("e:") }.mapNotNull { (k, v) ->
            val pkg = k.removePrefix("e:")
            val installed = try { pm.getApplicationInfo(pkg, 0); true } catch (e: Exception) { false }
            if (!installed || pkg in blocked) { p.edit().remove(k).apply(); return@mapNotNull null }  // fixed: stop
            val j = JSONObject(v as String)
            Entry(pkg, j.getString("t"), j.getString("b"), j.getLong("f"), j.getLong("l"), j.getInt("c"))
        }
        val sent = p.getString("sent", "")!!.split(",").mapNotNull { it.toLongOrNull() }.filter { it > now - 86_400_000L }
        val send = due(entries, now, sent.size)
        for (e in send) {
            AlertNotifier.show(ctx, e.title, e.body + if (e.count > 0) "\n(Reminder ${e.count})" else "", ("warn:" + e.pkg).hashCode())
            p.edit().putString("e:${e.pkg}", JSONObject().put("t", e.title).put("b", e.body).put("f", e.firstAt).put("l", now).put("c", e.count + 1).toString()).apply()
        }
        p.edit().putString("sent", (sent + send.map { now }).joinToString(",")).apply()
    }
}
