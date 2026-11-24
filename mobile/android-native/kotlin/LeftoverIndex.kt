package com.fraudshield.app

import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * 17.1 ownership index - on the phone only, never uploaded.
 *
 * After an app is removed Android forgets which shared files it made (MediaStore clears the owner), so FraudShield
 * remembers it WHILE the app is installed:
 *  - files: MediaStore OWNER_PACKAGE_NAME (Android 10+), for apps the user installed (not system apps)
 *  - folders: shared-storage folders named after the app (exact label / package-part match, see LeftoverRules.nameKeys)
 *  - labels: the app's name, so the notification can say "Telegram left ..." after the package is gone
 * Refreshed at most once a day (install events, opening the leftovers screen) and on every new install.
 * Also holds the "removed apps with leftovers" list the dashboard shows.
 */
class LeftoverIndex private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "leftovers.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE owned(path TEXT PRIMARY KEY, pkg TEXT NOT NULL, is_dir INTEGER NOT NULL, seen INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX owned_pkg ON owned(pkg)")
        db.execSQL("CREATE TABLE labels(pkg TEXT PRIMARY KEY, label TEXT NOT NULL, seen INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE removed(pkg TEXT PRIMARY KEY, label TEXT NOT NULL, at INTEGER NOT NULL, files INTEGER NOT NULL, bytes INTEGER NOT NULL, state TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    data class Removed(val pkg: String, val label: String, val at: Long, val files: Int, val bytes: Long, val state: String)

    @Synchronized fun owned(pkg: String): List<Pair<String, Boolean>> =
        readableDatabase.rawQuery("SELECT path, is_dir FROM owned WHERE pkg=?", arrayOf(pkg)).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to (c.getInt(1) == 1)) }
        }

    @Synchronized fun label(pkg: String): String? =
        readableDatabase.rawQuery("SELECT label FROM labels WHERE pkg=?", arrayOf(pkg)).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    @Synchronized fun putLabel(pkg: String, label: String) {
        writableDatabase.insertWithOnConflict("labels", null, ContentValues().apply {
            put("pkg", pkg); put("label", label); put("seen", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun markRemoved(pkg: String, label: String, files: Int, bytes: Long) {
        writableDatabase.insertWithOnConflict("removed", null, ContentValues().apply {
            put("pkg", pkg); put("label", label); put("at", System.currentTimeMillis()); put("files", files); put("bytes", bytes); put("state", "PENDING")
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized fun setState(pkg: String, state: String) {
        writableDatabase.update("removed", ContentValues().apply { put("state", state) }, "pkg=?", arrayOf(pkg))
        if (state != "PENDING") writableDatabase.delete("owned", "pkg=?", arrayOf(pkg)) // nothing more to remember
    }

    /** Pending leftovers younger than 30 days (older ones are dropped - the user has moved on). */
    @Synchronized fun pending(): List<Removed> {
        val cut = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        writableDatabase.delete("removed", "at<?", arrayOf(cut.toString()))
        return readableDatabase.rawQuery("SELECT pkg,label,at,files,bytes,state FROM removed WHERE state='PENDING' ORDER BY at DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(Removed(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3), c.getLong(4), c.getString(5))) }
        }
    }

    @Synchronized fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM owned", null).use { c -> c.moveToFirst(); c.getInt(0) }

    /** Rebuilds the index. Returns how many paths are known. Safe to call often; does the work at most every [maxAgeMs]. */
    fun refreshIfStale(ctx: Context, maxAgeMs: Long = 24L * 3600 * 1000): Int {
        val prefs = ctx.getSharedPreferences("fraudshield_leftovers", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong("indexed", 0) < maxAgeMs) return count()
        val n = refresh(ctx)
        prefs.edit().putLong("indexed", System.currentTimeMillis()).apply()
        return n
    }

    @Synchronized fun refresh(ctx: Context): Int {
        val pm = ctx.packageManager
        val apps = try { pm.getInstalledApplications(0) } catch (e: Exception) { emptyList() }
        val userApps = apps.filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != ctx.packageName }
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (a in userApps) {
                db.insertWithOnConflict("labels", null, ContentValues().apply {
                    put("pkg", a.packageName); put("label", a.loadLabel(pm).toString()); put("seen", now) }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            val users = userApps.map { it.packageName }.toSet()
            // files: MediaStore owner (Android 10+)
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    ctx.contentResolver.query(MediaStore.Files.getContentUri("external"),
                        arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.OWNER_PACKAGE_NAME),
                        "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} IS NOT NULL", null, null)?.use { c ->
                        while (c.moveToNext()) {
                            val path = c.getString(0) ?: continue
                            val owner = c.getString(1) ?: continue
                            if (owner !in users) continue
                            db.insertWithOnConflict("owned", null, ContentValues().apply {
                                put("path", path); put("pkg", owner); put("is_dir", 0); put("seen", now) }, SQLiteDatabase.CONFLICT_REPLACE)
                        }
                    }
                } catch (e: Exception) { android.util.Log.w("FraudShield", "leftover index: media query failed ${e.javaClass.simpleName}") }
            }
            // folders named after an installed user app
            val keysByPkg = userApps.associate { it.packageName to LeftoverRules.nameKeys(it.packageName, it.loadLabel(pm).toString()) }
            for (dir in candidateFolders()) {
                val owners = keysByPkg.filter { (_, keys) -> LeftoverRules.folderMatches(dir.name, keys) }.keys
                if (owners.size != 1) continue // ambiguous name: remember nothing rather than guess
                db.insertWithOnConflict("owned", null, ContentValues().apply {
                    put("path", dir.absolutePath); put("pkg", owners.first()); put("is_dir", 1); put("seen", now) }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            // cap: keep the newest 200k paths
            db.execSQL("DELETE FROM owned WHERE path IN (SELECT path FROM owned ORDER BY seen DESC LIMIT -1 OFFSET 200000)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return count()
    }

    companion object {
        @Volatile private var inst: LeftoverIndex? = null
        fun get(ctx: Context): LeftoverIndex = inst ?: synchronized(this) { inst ?: LeftoverIndex(ctx.applicationContext).also { inst = it } }

        fun root(): File = Environment.getExternalStorageDirectory()

        /** Folders at the storage root and one level under the standard folders (LeftoverRules.FOLDER_PARENTS). */
        fun candidateFolders(): List<File> {
            val r = root()
            return LeftoverRules.FOLDER_PARENTS.flatMap { parent ->
                val d = if (parent.isEmpty()) r else File(r, parent)
                (d.listFiles() ?: emptyArray()).filter { it.isDirectory && !(parent.isEmpty() && it.name.lowercase() in LeftoverRules.STANDARD_TOP) }
            }
        }
    }
}
