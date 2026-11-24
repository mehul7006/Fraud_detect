package com.fraudshield.app

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.ConnectivityManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 12.8: which app contacts which domains (counted from its DNS lookups) - local SQLite, 30-day rolling, never sent
 * anywhere. Bytes per app come from Android's own data-usage counters (NetworkStatsManager, needs Usage Access),
 * so they match Settings > Data usage.
 */
class TrafficStore private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "vpn_traffic.db", null, 1) {
    private val app = ctx.applicationContext
    private val pending = ConcurrentHashMap<String, AtomicInteger>() // "day|uid|domain" -> lookups

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE lookups (day INTEGER NOT NULL, uid INTEGER NOT NULL, domain TEXT NOT NULL, n INTEGER NOT NULL, PRIMARY KEY(day, uid, domain))")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    fun lookup(uid: Int, domain: String) { pending.computeIfAbsent("${today()}|$uid|$domain") { AtomicInteger() }.incrementAndGet() }

    @Synchronized fun flush() {
        if (pending.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (k in pending.keys.toList()) {
                val n = pending.remove(k)?.get() ?: continue
                val (day, uid, domain) = k.split("|", limit = 3)
                // update-then-insert: SQLite "upsert" needs Android 11+, the app supports 8+
                db.execSQL("UPDATE lookups SET n = n + ? WHERE day = ? AND uid = ? AND domain = ?", arrayOf<Any>(n, day.toLong(), uid.toInt(), domain))
                db.execSQL("INSERT OR IGNORE INTO lookups(day, uid, domain, n) VALUES(?,?,?,?)", arrayOf<Any>(day.toLong(), uid.toInt(), domain, n))
            }
            db.delete("lookups", "day < ?", arrayOf((today() - 30).toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Top apps of the last [days] days: lookups, distinct domains, top domains, and bytes (when Usage Access is on). */
    fun topApps(days: Int = 30, limit: Int = 20): List<Map<String, Any?>> {
        flush()
        val rows = mutableMapOf<Int, MutableMap<String, Int>>()
        readableDatabase.rawQuery("SELECT uid, domain, SUM(n) FROM lookups WHERE day >= ? GROUP BY uid, domain",
            arrayOf((today() - days + 1).toString())).use { c ->
            while (c.moveToNext()) rows.getOrPut(c.getInt(0)) { mutableMapOf() }[c.getString(1)] = c.getInt(2)
        }
        val bytes = bytesByUid(days)
        return rows.map { (uid, domains) ->
            mapOf("uid" to uid, "app" to (UidResolver.label(app, uid) ?: "Unknown app"), "package" to UidResolver.packageOf(app, uid),
                "lookups" to domains.values.sum(), "domains" to domains.size,
                "topDomains" to domains.entries.sortedByDescending { it.value }.take(8).map { it.key },
                "bytesRx" to bytes[uid]?.first, "bytesTx" to bytes[uid]?.second)
        }.sortedByDescending { (it["bytesRx"] as Long? ?: 0L) + (it["bytesTx"] as Long? ?: 0L) + (it["lookups"] as Int) }.take(limit)
    }

    /** 13.8: domains one app looked up in the last [days] days, most frequent first. */
    fun domainsOf(pkg: String, days: Int): List<Map<String, Any>> {
        flush()
        val uid = try { app.packageManager.getApplicationInfo(pkg, 0).uid } catch (e: Exception) { return emptyList() }
        val out = mutableListOf<Map<String, Any>>()
        readableDatabase.rawQuery("SELECT domain, SUM(n) AS c FROM lookups WHERE uid = ? AND day >= ? GROUP BY domain ORDER BY c DESC LIMIT 100",
            arrayOf(uid.toString(), (today() - days + 1).toString())).use { c ->
            while (c.moveToNext()) out += mapOf("domain" to c.getString(0), "count" to c.getInt(1))
        }
        return out
    }

    /** uid -> (received, sent) bytes over Wi-Fi + mobile, from Android's counters; empty without Usage Access. */
    private fun bytesByUid(days: Int): Map<Int, Pair<Long, Long>> {
        if (!UsageAuditor.hasUsageAccess(app)) return emptyMap()
        val nsm = app.getSystemService(NetworkStatsManager::class.java) ?: return emptyMap()
        val end = System.currentTimeMillis(); val start = end - days * 86_400_000L
        val out = mutableMapOf<Int, Pair<Long, Long>>()
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                nsm.querySummary(type, null, start, end).use { s ->
                    val b = NetworkStats.Bucket()
                    while (s.hasNextBucket()) {
                        s.getNextBucket(b)
                        val p = out[b.uid] ?: (0L to 0L)
                        out[b.uid] = (p.first + b.rxBytes) to (p.second + b.txBytes)
                    }
                }
            } catch (e: Exception) { /* no SIM / no permission for this type */ }
        }
        return out
    }

    private fun today() = System.currentTimeMillis() / 86_400_000L

    companion object {
        @Volatile private var inst: TrafficStore? = null
        fun get(ctx: Context) = inst ?: synchronized(this) { inst ?: TrafficStore(ctx.applicationContext).also { inst = it } }
    }
}
