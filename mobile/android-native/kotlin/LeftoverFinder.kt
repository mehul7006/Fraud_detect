package com.fraudshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import java.io.File
import java.nio.file.Files

/**
 * 17.2: right after an app is fully removed (InstallWatcher, PACKAGE_FULLY_REMOVED) collect what it left in shared
 * storage - files from the ownership index (17.1), files MediaStore still says it owned, and folders named after it -
 * and tell the user "X left N files (Y MB) - tap to review". 17.6: old APK installers in Download.
 * Nothing is deleted here; see LeftoverCleaner.
 */
object LeftoverFinder {
    const val EXTRA_OPEN = "fs_leftovers"
    private const val CH = "fraudshield_cleanup"
    private const val MAX_WALK = 20_000

    data class Item(val path: String, val rel: String, val isDir: Boolean, val bytes: Long, val files: Int,
                    val group: LeftoverRules.Group, val ticked: Boolean, val note: String = "") {
        fun toMap(): Map<String, Any> = mapOf("path" to path, "rel" to rel, "isDir" to isDir, "bytes" to bytes, "files" to files,
            "group" to group.name, "ticked" to ticked, "note" to note)
    }

    data class Stats(val bytes: Long, val files: Int, val byGroup: Map<LeftoverRules.Group, Long>)

    fun folderStats(dir: File): Stats {
        var bytes = 0L; var files = 0
        val byGroup = mutableMapOf<LeftoverRules.Group, Long>()
        val rootLen = dir.absolutePath.length
        dir.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.forEach { f ->
            if (files >= MAX_WALK || !f.isFile) return@forEach
            val len = f.length()
            bytes += len; files++
            val g = LeftoverRules.group(f.absolutePath.substring(rootLen))
            byGroup[g] = (byGroup[g] ?: 0) + len
        }
        return Stats(bytes, files, byGroup)
    }

    private fun rel(path: String): String = path.removePrefix(LeftoverIndex.root().absolutePath).trimStart('/')

    fun isInstalled(ctx: Context, pkg: String): Boolean =
        try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (e: PackageManager.NameNotFoundException) { false }

    /** Name keys of every installed app (system ones too) - their folders are never offered. */
    fun installedKeys(ctx: Context, except: String? = null): Set<String> {
        val pm = ctx.packageManager
        return (try { pm.getInstalledApplications(0) } catch (e: Exception) { emptyList() })
            .filter { it.packageName != except }
            .flatMap { LeftoverRules.nameKeys(it.packageName, it.loadLabel(pm).toString()) }.toSet()
    }

    /** Leftovers of [pkg] that still exist. Files inside a listed folder are counted in the folder, not listed twice. */
    fun find(ctx: Context, pkg: String, label: String?): List<Item> {
        val root = LeftoverIndex.root().canonicalPath
        val paths = linkedMapOf<String, Boolean>() // path -> isDir
        LeftoverIndex.get(ctx).owned(pkg).forEach { (p, d) -> paths[p] = d }
        if (Build.VERSION.SDK_INT >= 29) try { // in case MediaStore still knows the owner
            ctx.contentResolver.query(MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.MediaColumns.DATA),
                "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=?", arrayOf(pkg), null)?.use { c ->
                while (c.moveToNext()) c.getString(0)?.let { paths.putIfAbsent(it, false) }
            }
        } catch (e: Exception) {}
        val keys = LeftoverRules.nameKeys(pkg, label)
        val protectedKeys = installedKeys(ctx, except = pkg)
        for (dir in LeftoverIndex.candidateFolders()) {
            if (LeftoverRules.folderMatches(dir.name, keys) && LeftoverRules.norm(dir.name) !in protectedKeys) paths[dir.absolutePath] = true
        }
        val dirs = paths.filter { it.value }.keys.map { File(it) }.filter { it.isDirectory }.map { it.canonicalPath }
        val items = mutableListOf<Item>()
        for (d in dirs.distinct()) {
            if (dirs.any { it != d && d.startsWith("$it/") }) continue // a parent folder is listed already
            if (LeftoverRules.refuseReason(d, root, null, pkg) { false } != null) continue
            val s = folderStats(File(d))
            val g = LeftoverRules.folderGroup(s.byGroup)
            items.add(Item(d, rel(d), true, s.bytes, s.files, g, LeftoverRules.tickedByDefault(g)))
        }
        for ((p, isDir) in paths) {
            if (isDir) continue
            val f = File(p)
            if (!f.isFile) continue
            val c = f.canonicalPath
            if (dirs.any { c.startsWith("$it/") }) continue
            if (LeftoverRules.refuseReason(c, root, null, pkg) { false } != null) continue
            val g = LeftoverRules.group(rel(c))
            items.add(Item(c, rel(c), false, f.length(), 1, g, LeftoverRules.tickedByDefault(g)))
        }
        return items.sortedWith(compareBy<Item> { it.group.ordinal }.thenByDescending { it.bytes })
    }

    /** InstallWatcher: PACKAGE_FULLY_REMOVED. Runs off the main thread. */
    fun onRemoved(ctx: Context, pkg: String) {
        if (pkg == ctx.packageName) return
        val index = LeftoverIndex.get(ctx)
        val label = index.label(pkg) ?: pkg
        val items = try { find(ctx, pkg, index.label(pkg)) } catch (e: Exception) { emptyList() }
        val residue = try { SettingsResidueCheck.check(ctx, pkg).filter { it.state == LeftoverRules.State.LEFTOVER } } catch (e: Exception) { emptyList() }
        if (items.isEmpty() && residue.isEmpty()) { index.setState(pkg, "NONE"); return }
        val files = items.sumOf { it.files }
        val bytes = items.sumOf { it.bytes }
        index.markRemoved(pkg, label, files, bytes)
        val text = buildString {
            if (items.isNotEmpty()) append("$label left $files file(s) (${LeftoverRules.humanSize(bytes)}) on your phone. Tap to review - nothing is deleted without you.")
            if (residue.isNotEmpty()) append(" Settings still point to it: ${residue.joinToString { it.kind.lowercase().replace('_', ' ') }}.")
        }.trim()
        notify(ctx, pkg, "Leftovers of $label", text)
    }

    private fun notify(ctx: Context, pkg: String, title: String, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Leftover files after uninstall", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, ("left:$pkg").hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(("left:$pkg").hashCode(), NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_menu_delete).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build())
    }

    // ---------------- 17.6 old APK installers ----------------
    fun oldApks(ctx: Context, flagged: Set<String>): List<Map<String, Any?>> {
        val dl = File(LeftoverIndex.root(), "Download")
        if (!dl.isDirectory) return emptyList()
        val pm = ctx.packageManager
        val out = mutableListOf<Map<String, Any?>>()
        dl.walkTopDown().maxDepth(3).onEnter { !Files.isSymbolicLink(it.toPath()) }.forEach { f ->
            if (!f.isFile || LeftoverRules.group(f.name) != LeftoverRules.Group.INSTALLER) return@forEach
            val info = if (f.name.lowercase().endsWith(".apk")) try { pm.getPackageArchiveInfo(f.absolutePath, 0) } catch (e: Exception) { null } else null
            info?.applicationInfo?.let { it.sourceDir = f.absolutePath; it.publicSourceDir = f.absolutePath }
            val apkPkg = info?.packageName
            val apkVer = if (info == null) 0L else if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
            val installed = apkPkg?.let { p ->
                try { pm.getPackageInfo(p, 0).let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong() } }
                catch (e: PackageManager.NameNotFoundException) { null }
            }
            val status = LeftoverRules.apkStatus(apkPkg, apkVer, installed, f.canonicalPath in flagged || f.absolutePath in flagged)
            out.add(mapOf("path" to f.canonicalPath, "rel" to rel(f.canonicalPath), "bytes" to f.length(), "package" to apkPkg,
                "label" to (info?.applicationInfo?.loadLabel(pm)?.toString() ?: f.name), "version" to info?.versionName,
                "status" to status.name, "ticked" to LeftoverRules.apkTicked(status)))
        }
        return out.sortedByDescending { it["bytes"] as Long }
    }
}

/** 17.5: folders of popular apps that are no longer installed (also apps removed before FraudShield was installed). */
object OrphanScanner {
    fun known(ctx: Context): List<LeftoverRules.Known> = try {
        ctx.assets.open("flutter_assets/assets/leftovers/known-folders.json").bufferedReader().use { LeftoverRules.parseKnown(it.readText()) }
    } catch (e: Exception) { emptyList() }

    fun scan(ctx: Context): List<Map<String, Any>> {
        val root = LeftoverIndex.root()
        val existing = LeftoverIndex.candidateFolders().map { it.absolutePath.removePrefix(root.absolutePath).trimStart('/') }
        val pm = ctx.packageManager
        val installed = (try { pm.getInstalledApplications(0) } catch (e: Exception) { emptyList() }).map { it.packageName }.toSet()
        return LeftoverRules.orphans(existing, known(ctx), installed, LeftoverFinder.installedKeys(ctx)).map { (rel, k) ->
            val dir = File(root, rel)
            val s = LeftoverFinder.folderStats(dir)
            val g = LeftoverRules.folderGroup(s.byGroup)
            LeftoverFinder.Item(dir.canonicalPath, rel, true, s.bytes, s.files, g, LeftoverRules.tickedByDefault(g), k.app).toMap()
        }
    }
}

/** 17.7: after removal, do accessibility / notification access / device admin / Always-on VPN / default apps still name it? */
object SettingsResidueCheck {
    fun check(ctx: Context, pkg: String): List<LeftoverRules.Check> {
        val cr = ctx.contentResolver
        fun secure(key: String): String? = try { android.provider.Settings.Secure.getString(cr, key) ?: "" } catch (e: Exception) { null }
        val admins = try {
            (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager).activeAdmins?.map { it.packageName } ?: emptyList()
        } catch (e: Exception) { null }
        // "always_on_vpn_app" is not a public setting: newer Android may refuse to tell us - then it says "could not check"
        val vpnRead = try { android.provider.Settings.Secure.getString(cr, "always_on_vpn_app") to true } catch (e: Exception) { null to false }
        val pm = ctx.packageManager
        fun resolve(i: Intent): String? = try { pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName } catch (e: Exception) { null }
        val defaults = mapOf(
            "SMS" to try { android.provider.Telephony.Sms.getDefaultSmsPackage(ctx) } catch (e: Exception) { null },
            "DIALER" to try { (ctx.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager).defaultDialerPackage } catch (e: Exception) { null },
            "BROWSER" to resolve(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://example.com"))),
            "HOME" to resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)))
        return LeftoverRules.settingsResidue(pkg, secure("enabled_accessibility_services"), secure("enabled_notification_listeners"),
            admins, vpnRead.first, vpnRead.second, defaults)
    }
}
