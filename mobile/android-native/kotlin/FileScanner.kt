package com.fraudshield.app

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Walks the folders where scammers' files land (Downloads, WhatsApp, Telegram) and returns metadata of files
 * not scanned before. Needs All-files access (MANAGE_EXTERNAL_STORAGE) - Play-restricted, see docs/01.
 * The file never leaves the phone: only sha256, name, size, first 8 bytes and content flag NAMES.
 *
 * Folder roots (5.4.1/5.4.2). From the apps' documented storage behaviour; only "Download" is confirmed on the
 * test emulator (no WhatsApp/Telegram installed there):
 *  - Download                                                   browser / any app saving to Downloads
 *  - Android/media/com.whatsapp/WhatsApp/Media                  WhatsApp on Android 11+ (scoped storage)
 *  - WhatsApp/Media                                             WhatsApp on Android 10 and older
 *  - Android/media/com.whatsapp.w4b/WhatsApp Business/Media     WhatsApp Business on Android 11+
 *  - WhatsApp Business/Media                                    WhatsApp Business on Android 10 and older
 *  - Android/media/org.telegram.messenger/Telegram              Telegram on Android 11+ (Documents, Audio, Video...)
 *  - Download/Telegram, Telegram                                Telegram older versions / "save to downloads"
 */
object FileScanner {
    private const val MAX_SIZE = 200L * 1024 * 1024
    private const val SETTLE_MS = 3_000L // a file changed in the last 3 s may still be downloading
    private val PARTIAL_SUFFIX = listOf(".crdownload", ".part", ".tmp", ".download", ".opdownload", ".partial")

    private fun roots(): List<Pair<File, String>> {
        val ext = Environment.getExternalStorageDirectory()
        return listOf(
            File(ext, "Download") to "downloads",
            File(ext, "Android/media/com.whatsapp/WhatsApp/Media") to "com.whatsapp",
            File(ext, "WhatsApp/Media") to "com.whatsapp",
            File(ext, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media") to "com.whatsapp.w4b",
            File(ext, "WhatsApp Business/Media") to "com.whatsapp.w4b",
            File(ext, "Android/media/org.telegram.messenger/Telegram") to "org.telegram.messenger",
            File(ext, "Download/Telegram") to "org.telegram.messenger",
            File(ext, "Telegram") to "org.telegram.messenger"
        ).filter { it.first.isDirectory }
    }

    /** Pure rule: true when the file looks like an unfinished download or hidden helper file. */
    internal fun looksUnfinished(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith(".") || PARTIAL_SUFFIX.any { n.endsWith(it) }
    }

    private fun baseline(ctx: Context): BaselineStore {
        val store = File(ctx.filesDir, "file_baseline.txt")
        return BaselineStore(
            load = { if (store.exists()) store.readLines() else null },
            save = { ids -> store.writeText(ids.joinToString("\n")) }
        )
    }

    private val storeLock = Any()
    private var store: BaselineStore? = null

    /** Call from a background thread: hashing can take seconds for big files. */
    fun scanNew(ctx: Context): List<Map<String, Any>> {
        val now = System.currentTimeMillis()
        val settled = mutableListOf<Triple<File, String, String>>() // file, source, id
        for ((root, src) in roots()) {
            root.walkTopDown().maxDepth(6)
                .filter { it.isFile && it.length() in 1..MAX_SIZE && !looksUnfinished(it.name) && now - it.lastModified() >= SETTLE_MS }
                .forEach { settled.add(Triple(it, src, BaselineStore.idOf(it.absolutePath, it.lastModified()))) }
        }
        val s = synchronized(storeLock) { store ?: baseline(ctx).also { store = it } }
        val fresh = s.newOnly(settled.map { it.third }).toSet()
        return settled.filter { it.third in fresh }.map { (f, src, _) ->
            mapOf(
                "path" to f.absolutePath, "fileName" to f.name, "sizeBytes" to f.length(),
                "sha256" to sha256(f), "headerHex" to header(f), "sourceApp" to src,
                "flags" to ContentFlagger.flags(f)
            )
        }
    }

    /**
     * Pure rule (5.7): a file may be deleted only when its canonical path is strictly inside a scan root.
     * The trailing separator stops "Download-evil" from matching "Download"; the root itself is not deletable.
     */
    internal fun isInsideRoot(rootCanonical: String, fileCanonical: String): Boolean {
        val prefix = if (rootCanonical.endsWith(File.separator)) rootCanonical else rootCanonical + File.separator
        return fileCanonical.startsWith(prefix) && fileCanonical.length > prefix.length
    }

    fun delete(path: String): Boolean {
        if (path.contains("..")) return false
        val f = File(path)
        if (!f.exists()) return false
        if (Files.isSymbolicLink(f.toPath())) return false
        val canon = f.canonicalPath
        val ok = roots().any { isInsideRoot(it.first.canonicalPath, canon) }
        return ok && f.delete()
    }

    internal fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(f).use { s -> val buf = ByteArray(8192); while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun header(f: File): String = FileInputStream(f).use { s ->
        val b = ByteArray(8); val n = s.read(b); b.take(maxOf(n, 0)).joinToString("") { "%02x".format(it) }
    }
}
