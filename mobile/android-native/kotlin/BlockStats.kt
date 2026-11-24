package com.fraudshield.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 18.5: what the blocker blocked - per app, per category, top domains - kept ON THE PHONE only (local SQLite,
 * 30 days), never sent anywhere. Counted like TrafficStore (12.8): in memory, flushed every 30 s by the VPN.
 * "Data saved" is an estimate (blocked requests x an assumed average response), labelled as such in the app.
 */
class BlockStats private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "block_stats.db", null, 1) {
    private val app = ctx.applicationContext
    private val pending = ConcurrentHashMap<String, AtomicInteger>() // "day|uid|cat|domain" -> count

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE blocked (day INTEGER NOT NULL, uid INTEGER NOT NULL, cat INTEGER NOT NULL, domain TEXT NOT NULL, n INTEGER NOT NULL, PRIMARY KEY(day, uid, cat, domain))")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    fun record(uid: Int, cat: Int, domain: String) {
        total.incrementAndGet()
        pending.computeIfAbsent("${today()}|$uid|$cat|$domain") { AtomicInteger() }.incrementAndGet()
    }

    @Synchronized fun flush() {
        if (pending.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (k in pending.keys.toList()) {
                val n = pending.remove(k)?.get() ?: continue
                val (day, uid, cat, domain) = k.split("|", limit = 4)
                db.execSQL("UPDATE blocked SET n = n + ? WHERE day = ? AND uid = ? AND cat = ? AND domain = ?", arrayOf<Any>(n, day.toLong(), uid.toInt(), cat.toInt(), domain))
                db.execSQL("INSERT OR IGNORE INTO blocked(day, uid, cat, domain, n) VALUES(?,?,?,?,?)", arrayOf<Any>(day.toLong(), uid.toInt(), cat.toInt(), domain, n))
            }
            db.execSQL("DELETE FROM blocked WHERE day < ?", arrayOf<Any>(today() - 30))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Rows of the last [days] days (flushes first so the numbers match the VPN counters). */
    fun rows(days: Int): List<Row> {
        flush()
        val out = ArrayList<Row>()
        readableDatabase.rawQuery("SELECT uid, cat, domain, SUM(n) FROM blocked WHERE day > ? GROUP BY uid, cat, domain", arrayOf((today() - days).toString())).use { c ->
            while (c.moveToNext()) out += Row(c.getInt(0), c.getInt(1), c.getString(2), c.getInt(3))
        }
        return out
    }

    fun summary(days: Int): Map<String, Any?> {
        val s = summarize(rows(days))
        val pm = app.packageManager
        return mapOf(
            "total" to s.total, "byCategory" to s.byCategory, "estimatedBytesSaved" to s.estimatedBytesSaved,
            "topDomains" to s.topDomains.map { mapOf("domain" to it.first, "category" to BlockList.NAMES[it.second], "count" to it.third) },
            "byApp" to s.byApp.map { (uid, n) ->
                val pkg = UidResolver.packageOf(app, uid)
                val label = pkg?.let { try { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() } catch (_: Exception) { it } }
                mapOf("uid" to uid, "package" to pkg, "label" to (label ?: if (uid < 0) "Unknown app" else "uid $uid"), "count" to n)
            },
            "sinceStart" to total.get(),
        )
    }

    data class Row(val uid: Int, val cat: Int, val domain: String, val n: Int)
    data class Summary(val total: Int, val byCategory: Map<String, Int>, val byApp: List<Pair<Int, Int>>,
                       val topDomains: List<Triple<String, Int, Int>>, val estimatedBytesSaved: Long)

    private fun today() = System.currentTimeMillis() / 86_400_000L

    companion object {
        /** Assumed average size of one blocked ad / tracker response (an estimate, shown as one). */
        const val AVG_BYTES = 20_000L
        /** Blocked lookups since the VPN process started (matches the VPN's counter). */
        val total = AtomicLong()

        @Volatile private var inst: BlockStats? = null
        fun get(ctx: Context) = inst ?: synchronized(this) { inst ?: BlockStats(ctx.applicationContext).also { inst = it } }

        /** Pure (unit-tested). */
        fun summarize(rows: List<Row>, top: Int = 20): Summary {
            val byCat = rows.groupBy { it.cat }.mapValues { e -> e.value.sumOf { it.n } }
            return Summary(
                total = rows.sumOf { it.n },
                byCategory = BlockList.NAMES.entries.associate { it.value to (byCat[it.key] ?: 0) },
                byApp = rows.groupBy { it.uid }.map { it.key to it.value.sumOf { r -> r.n } }.sortedByDescending { it.second },
                topDomains = rows.groupBy { it.domain }.map { (d, rs) -> Triple(d, rs.maxBy { it.n }.cat, rs.sumOf { it.n }) }
                    .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.third }.thenBy { it.first }).take(top),
                estimatedBytesSaved = rows.sumOf { it.n } * AVG_BYTES,
            )
        }
    }
}
