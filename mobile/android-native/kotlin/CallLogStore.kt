package com.fraudshield.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 16.10: screened calls, on the phone only, kept 30 days. Plain inserts + updates (no UPSERT: Android 8-10 SQLite).
 * The number is stored here so the user can see it, undo or report it; it never leaves the phone except on Report.
 */
class CallLogStore private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "call_guard.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE calls (id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, number TEXT NOT NULL, " +
            "action TEXT NOT NULL, kind TEXT NOT NULL, label TEXT NOT NULL, rule TEXT, category TEXT, undone INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX calls_at ON calls(at)")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    @Synchronized fun add(number: String, d: CallGuardEngine.Decision, label: String): Long {
        val db = writableDatabase
        db.delete("calls", "at < ?", arrayOf((System.currentTimeMillis() - KEEP_MS).toString()))
        return db.insert("calls", null, ContentValues().apply {
            put("at", System.currentTimeMillis()); put("number", number); put("action", d.action.name)
            put("kind", d.kind.name); put("label", label); put("rule", d.rule?.id); put("category", d.scamCategory)
        })
    }

    fun recent(days: Int = 30): List<Map<String, Any?>> {
        val since = System.currentTimeMillis() - days.coerceIn(1, 30) * 86_400_000L
        readableDatabase.rawQuery("SELECT id, at, number, action, kind, label, rule, category, undone FROM calls WHERE at >= ? ORDER BY at DESC LIMIT 500",
            arrayOf(since.toString())).use { c ->
            val out = ArrayList<Map<String, Any?>>()
            while (c.moveToNext()) out += mapOf("id" to c.getLong(0), "at" to c.getLong(1), "number" to c.getString(2),
                "action" to c.getString(3), "kind" to c.getString(4), "label" to c.getString(5), "rule" to c.getString(6),
                "category" to c.getString(7), "undone" to (c.getInt(8) == 1))
            return out
        }
    }

    /** "Not spam": every logged call from this number is marked undone. */
    @Synchronized fun markUndone(number: String) =
        writableDatabase.update("calls", ContentValues().apply { put("undone", 1) }, "number = ?", arrayOf(number))

    fun counts(days: Int = 30): Map<String, Int> {
        val since = System.currentTimeMillis() - days * 86_400_000L
        readableDatabase.rawQuery("SELECT action, COUNT(*) FROM calls WHERE at >= ? GROUP BY action", arrayOf(since.toString())).use { c ->
            val m = HashMap<String, Int>(); while (c.moveToNext()) m[c.getString(0)] = c.getInt(1); return m
        }
    }

    companion object {
        const val KEEP_MS = 30L * 86_400_000
        @Volatile private var inst: CallLogStore? = null
        fun get(ctx: Context) = inst ?: synchronized(this) { inst ?: CallLogStore(ctx.applicationContext).also { inst = it } }
    }
}
