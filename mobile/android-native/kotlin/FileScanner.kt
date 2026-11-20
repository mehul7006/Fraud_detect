package com.fraudshield.app

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Walks the folders where scammers' files land (Downloads, WhatsApp, Telegram) and returns metadata of files
 * not scanned before. Needs All-files access (MANAGE_EXTERNAL_STORAGE) - Play-restricted, see docs/01.
 * The file never leaves the phone: only sha256, name, size and first 8 bytes.
 */
object FileScanner {
    private const val PREFS = "fraudshield_files"
    private const val MAX_SIZE = 200L * 1024 * 1024

    private fun roots(): List<Pair<File, String>> {
        val ext = Environment.getExternalStorageDirectory()
        return listOf(
            File(ext, "Download") to "downloads",
            File(ext, "Android/media/com.whatsapp/WhatsApp/Media") to "com.whatsapp",
            File(ext, "WhatsApp/Media") to "com.whatsapp",
            File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media") to "com.whatsapp.w4b",
            File(ext, "Download/Telegram") to "org.telegram.messenger",
            File(ext, "Telegram") to "org.telegram.messenger"
        ).filter { it.first.isDirectory }
    }

    fun scanNew(ctx: Context): List<Map<String, Any>> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen", emptySet())!!.toMutableSet()
        val firstRun = !prefs.contains("seen")
        val out = mutableListOf<Map<String, Any>>()
        for ((root, src) in roots()) {
            root.walkTopDown().maxDepth(6).filter { it.isFile && it.length() in 1..MAX_SIZE }.forEach { f ->
                val id = f.absolutePath + "|" + f.lastModified()
                if (id in seen) return@forEach
                seen.add(id)
                if (firstRun) return@forEach // baseline: don't flag the whole existing library on first run
                out.add(mapOf(
                    "path" to f.absolutePath, "fileName" to f.name, "sizeBytes" to f.length(),
                    "sha256" to sha256(f), "headerHex" to header(f), "sourceApp" to src
                ))
            }
        }
        prefs.edit().putStringSet("seen", seen.toList().takeLast(5000).toSet()).apply()
        return out
    }

    fun delete(path: String): Boolean {
        val f = File(path)
        // only delete inside known scan roots
        val ok = roots().any { f.canonicalPath.startsWith(it.first.canonicalPath) }
        return ok && f.exists() && f.delete()
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(f).use { s -> val buf = ByteArray(8192); while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun header(f: File): String = FileInputStream(f).use { s ->
        val b = ByteArray(8); val n = s.read(b); b.take(maxOf(n, 0)).joinToString("") { "%02x".format(it) }
    }
}
