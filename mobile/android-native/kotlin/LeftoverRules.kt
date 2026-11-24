package com.fraudshield.app

/**
 * Phase 17 (leftover cleaner): every decision that does not need Android APIs, so it is unit tested on the JVM.
 *
 *  - which group a leftover file belongs to and whether it is ticked by default (17.3: media and documents never are)
 *  - which shared-storage folders are named after an app (17.1 / 17.2) - exact name match only, never fuzzy
 *  - which known folders are orphans (17.5) - never a folder of an installed app
 *  - old APK installers (17.6)
 *  - whether a path may be deleted at all (17.4) - only shared storage, never Android/, never the standard folders
 *    themselves, never a file another installed app owns
 *  - "nothing left in Settings" (17.7)
 */
object LeftoverRules {
    enum class Group { INSTALLER, CACHE, MEDIA, DOCUMENT, OTHER }

    private val INSTALLER_EXT = setOf("apk", "apks", "xapk", "apkm")
    private val CACHE_EXT = setOf("tmp", "temp", "log", "cache", "bak", "part", "crdownload", "partial", "download", "trace")
    private val MEDIA_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "mp4", "mkv", "3gp", "webm", "mov",
        "avi", "mp3", "m4a", "aac", "ogg", "opus", "wav", "amr", "flac")
    private val DOC_EXT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "odt", "ods", "odp", "rtf", "epub", "vcf")
    private val CACHE_DIRS = setOf("cache", ".cache", "temp", "tmp", ".tmp", ".thumbnails", "thumbnails", "thumb", "log", "logs", ".logs", "crash", "crashes")

    /** Standard folders: never deleted themselves (their sub-folders can be). Lower case. */
    val STANDARD_TOP = setOf("android", "dcim", "pictures", "download", "documents", "movies", "music", "alarms", "notifications",
        "podcasts", "ringtones", "audiobooks", "recordings", "screenshots")

    /** Where apps create their own folders: the storage root and one level below these standard folders. */
    val FOLDER_PARENTS = listOf("", "Pictures", "Movies", "Music", "Documents", "Download", "DCIM")

    fun group(relPath: String): Group {
        val p = relPath.replace('\\', '/').lowercase()
        val name = p.substringAfterLast('/')
        val ext = if (name.contains('.')) name.substringAfterLast('.') else ""
        if (ext in INSTALLER_EXT) return Group.INSTALLER
        val dirs = p.split('/').dropLast(1)
        if (ext in CACHE_EXT || name.startsWith(".") || dirs.any { it in CACHE_DIRS }) return Group.CACHE
        if (ext in MEDIA_EXT) return Group.MEDIA
        if (ext in DOC_EXT) return Group.DOCUMENT
        return Group.OTHER
    }

    /** 17.3 safe defaults: only installers and cache are pre-ticked; photos, videos, documents and unknown files never. */
    fun tickedByDefault(g: Group): Boolean = g == Group.INSTALLER || g == Group.CACHE

    /** A folder's group = the group holding most of its bytes (so a folder full of photos is MEDIA, not ticked). */
    fun folderGroup(bytesByGroup: Map<Group, Long>): Group {
        if (bytesByGroup.isEmpty()) return Group.CACHE // empty folder
        // anything personal inside makes the folder personal: never pre-tick a folder that holds photos or documents
        if ((bytesByGroup[Group.MEDIA] ?: 0) > 0) return Group.MEDIA
        if ((bytesByGroup[Group.DOCUMENT] ?: 0) > 0) return Group.DOCUMENT
        return bytesByGroup.maxByOrNull { it.value }!!.key
    }

    // ---------------- names ----------------
    private val GENERIC = setOf("com", "org", "net", "android", "app", "apps", "mobile", "free", "pro", "lite", "gps", "the",
        "studio", "google", "main", "release", "beta", "plus", "india", "official", "client", "global", "play", "store", "inc",
        "camera", "gallery", "photos", "photo", "video", "videos", "music", "files", "file", "download", "downloads", "documents",
        "pictures", "movies", "screenshots", "recordings", "media", "backup", "backups", "data", "cache", "notes", "messages", "phone",
        "messenger", "chat", "social", "share", "player", "editor", "scanner", "browser", "launcher", "keyboard", "wallet", "bank")

    fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Names a folder of this app could have: the app's label and the distinctive parts of its package
     * ("org.telegram.messenger" -> telegram, messenger; "com.lenovo.anyshare.gps" -> lenovo, anyshare).
     * Generic words (camera, photos, google, ...) never count - they would match folders of other apps.
     */
    fun nameKeys(pkg: String, label: String?): Set<String> {
        val keys = linkedSetOf<String>()
        label?.let { val n = norm(it); if (n.length >= 4 && n !in GENERIC) keys.add(n) }
        pkg.split('.').drop(1).map(::norm).filter { it.length >= 4 && it !in GENERIC }.forEach { keys.add(it) }
        return keys
    }

    /** Exact match only: folder "Telegram" belongs to a key "telegram"; "TelegramX" does not. */
    fun folderMatches(folderName: String, keys: Set<String>): Boolean = norm(folderName).let { it.isNotEmpty() && it in keys }

    // ---------------- 17.5 orphans ----------------
    data class Known(val packages: List<String>, val folder: String, val app: String)

    /**
     * [existing] = relative paths of folders that exist (e.g. "SHAREit", "Pictures/Telegram").
     * A known folder is an orphan only when NONE of its packages is installed and its name does not match any installed
     * app's name keys either (a renamed / cloned / "lite" variant keeps its folder).
     */
    fun orphans(existing: List<String>, known: List<Known>, installedPkgs: Set<String>, installedKeys: Set<String>): List<Pair<String, Known>> =
        existing.mapNotNull { rel ->
            val k = known.firstOrNull { it.folder.equals(rel, ignoreCase = true) } ?: return@mapNotNull null
            if (k.packages.any { it in installedPkgs }) return@mapNotNull null
            if (norm(rel.substringAfterLast('/')) in installedKeys) return@mapNotNull null
            rel to k
        }

    /** Tiny parser for known-folders.json: [{"app":"..","packages":[..],"folders":[..]}, ...] (no JSON lib on the JVM tests). */
    fun parseKnown(json: String): List<Known> {
        val out = mutableListOf<Known>()
        val obj = Regex("\\{[^{}]*\\}").findAll(json)
        for (m in obj) {
            val o = m.value
            val app = Regex("\"app\"\\s*:\\s*\"([^\"]*)\"").find(o)?.groupValues?.get(1) ?: continue
            fun arr(name: String) = Regex("\"$name\"\\s*:\\s*\\[([^\\]]*)\\]").find(o)?.groupValues?.get(1)
                ?.let { Regex("\"([^\"]*)\"").findAll(it).map { x -> x.groupValues[1] }.toList() } ?: emptyList()
            val pkgs = arr("packages")
            for (f in arr("folders")) out.add(Known(pkgs, f, app))
        }
        return out
    }

    // ---------------- 17.6 old APKs ----------------
    enum class ApkStatus { FLAGGED, SAME_INSTALLED, OLDER_THAN_INSTALLED, NEWER_THAN_INSTALLED, NOT_INSTALLED, UNREADABLE }

    fun apkStatus(apkPkg: String?, apkVersion: Long, installedVersion: Long?, flagged: Boolean): ApkStatus = when {
        flagged -> ApkStatus.FLAGGED
        apkPkg == null -> ApkStatus.UNREADABLE
        installedVersion == null -> ApkStatus.NOT_INSTALLED
        apkVersion == installedVersion -> ApkStatus.SAME_INSTALLED
        apkVersion < installedVersion -> ApkStatus.OLDER_THAN_INSTALLED
        else -> ApkStatus.NEWER_THAN_INSTALLED
    }

    /** Pre-ticked: dangerous, or useless because the same / a newer version is installed. Others are listed, not ticked. */
    fun apkTicked(s: ApkStatus): Boolean = s == ApkStatus.FLAGGED || s == ApkStatus.SAME_INSTALLED || s == ApkStatus.OLDER_THAN_INSTALLED

    // ---------------- 17.4 may this path be deleted? ----------------
    /**
     * null = allowed, otherwise the reason it is refused. [canonical] and [root] are canonical paths
     * (root = shared storage, e.g. /storage/emulated/0). [owner] = MediaStore owner of the file (null = none / unknown),
     * [removedPkg] = the app whose leftovers these are (null for orphan / APK cleaning).
     */
    fun refuseReason(canonical: String, root: String, owner: String?, removedPkg: String?, isInstalled: (String) -> Boolean): String? {
        val r = root.trimEnd('/')
        if (!canonical.startsWith("$r/")) return "OUTSIDE_SHARED_STORAGE"
        val rel = canonical.removePrefix("$r/").trimEnd('/')
        if (rel.isEmpty() || rel.split('/').any { it == ".." }) return "OUTSIDE_SHARED_STORAGE"
        val parts = rel.lowercase().split('/')
        if (parts[0] == "android") return "APP_PRIVATE_AREA" // Android/data, obb, media: belong to installed apps / the system
        if (parts.size == 1 && parts[0] in STANDARD_TOP) return "STANDARD_FOLDER"
        if (parts.size == 2 && parts[0] == "dcim" && parts[1] == "camera") return "STANDARD_FOLDER"
        if (owner != null && owner != removedPkg && isInstalled(owner)) return "OWNED_BY_INSTALLED_APP"
        return null
    }

    // ---------------- 17.7 nothing left in Settings ----------------
    enum class State { CLEAR, LEFTOVER, UNKNOWN }
    data class Check(val kind: String, val state: State, val detail: String = "")

    /** "pkg/.Cls:pkg2/pkg2.Cls" (Settings.Secure lists) -> packages. */
    fun packagesOf(setting: String?): List<String> =
        setting?.split(':')?.mapNotNull { it.substringBefore('/').trim().ifEmpty { null } } ?: emptyList()

    /**
     * Each input is null when Android did not let us read it (shown as "could not check", never as "clear").
     * [defaults] = role name -> package currently holding it (sms, dialer, browser, home, assistant).
     */
    fun settingsResidue(pkg: String, accessibility: String?, listeners: String?, admins: List<String>?, alwaysOnVpn: String?,
                        alwaysOnReadable: Boolean, defaults: Map<String, String?>): List<Check> {
        fun list(kind: String, s: String?) = when {
            s == null -> Check(kind, State.UNKNOWN)
            pkg in packagesOf(s) -> Check(kind, State.LEFTOVER)
            else -> Check(kind, State.CLEAR)
        }
        val out = mutableListOf(
            list("ACCESSIBILITY", accessibility),
            list("NOTIFICATION_ACCESS", listeners),
            when { admins == null -> Check("DEVICE_ADMIN", State.UNKNOWN); pkg in admins -> Check("DEVICE_ADMIN", State.LEFTOVER); else -> Check("DEVICE_ADMIN", State.CLEAR) },
            when { !alwaysOnReadable -> Check("ALWAYS_ON_VPN", State.UNKNOWN); alwaysOnVpn == pkg -> Check("ALWAYS_ON_VPN", State.LEFTOVER); else -> Check("ALWAYS_ON_VPN", State.CLEAR) },
        )
        for ((role, holder) in defaults) out.add(if (holder == pkg) Check("DEFAULT_$role", State.LEFTOVER, role) else Check("DEFAULT_$role", State.CLEAR, role))
        return out
    }

    // ---------------- text ----------------
    fun humanSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(java.util.Locale.ROOT, "%.1f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
