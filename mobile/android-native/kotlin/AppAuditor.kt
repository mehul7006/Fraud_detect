package com.fraudshield.app

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Lists installed apps with their risky abilities (output matches the backend AppInfo DTO). Needs QUERY_ALL_PACKAGES.
 *
 * 6.5: abilities are what the app can REALLY do now, not what it asks for in its manifest:
 *  - overlay      = the "Display over other apps" app-op is allowed (requesting SYSTEM_ALERT_WINDOW is not enough)
 *  - accessibility = one of its accessibility services is switched ON in Settings
 *  - notification listener = it is switched ON in Notification access
 *  - SMS stays "declares READ_SMS/RECEIVE_SMS" so the 3.7 audit also lists apps that could be granted it in one tap;
 *    smsGranted carries the granted state.
 *
 * 6.2: certSha256 (signing certificate) and installer (com.android.vending = Google Play) let the backend tell the
 * real WhatsApp from a sideloaded look-alike that uses the same package name.
 */
object AppAuditor {
    private fun enabledPackages(ctx: Context, key: String): Set<String> =
        (Settings.Secure.getString(ctx.contentResolver, key) ?: "").split(":")
            .mapNotNull { ComponentName.unflattenFromString(it)?.packageName }.toSet()

    private fun overlayAllowed(ctx: Context, pm: PackageManager, pkg: String, uid: Int, requested: Boolean): Boolean {
        if (!requested) return false
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = try {
            if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg)
            else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg)
        } catch (e: Exception) { AppOpsManager.MODE_DEFAULT }
        return mode == AppOpsManager.MODE_ALLOWED ||
            (mode == AppOpsManager.MODE_DEFAULT && pm.checkPermission(Manifest.permission.SYSTEM_ALERT_WINDOW, pkg) == PackageManager.PERMISSION_GRANTED)
    }

    @Suppress("DEPRECATION")
    private fun certs(p: android.content.pm.PackageInfo): String? = try {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = p.signingInfo ?: return null
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else p.signatures
        CertHash.join(sigs?.map { it.toByteArray() } ?: emptyList())
    } catch (e: Exception) { null }

    private fun installer(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
    } catch (e: Exception) { null }

    private fun categoryName(c: Int): String? = when (c) {
        ApplicationInfo.CATEGORY_GAME -> "GAME"; ApplicationInfo.CATEGORY_AUDIO -> "AUDIO"; ApplicationInfo.CATEGORY_VIDEO -> "VIDEO"
        ApplicationInfo.CATEGORY_IMAGE -> "IMAGE"; ApplicationInfo.CATEGORY_SOCIAL -> "SOCIAL"; ApplicationInfo.CATEGORY_NEWS -> "NEWS"
        ApplicationInfo.CATEGORY_MAPS -> "MAPS"; ApplicationInfo.CATEGORY_PRODUCTIVITY -> "PRODUCTIVITY"; else -> null
    }

    /** 13.2: requested permissions that are currently granted (runtime ones only count once the user allowed them). */
    private fun granted(p: android.content.pm.PackageInfo): List<String> {
        val names = p.requestedPermissions ?: return emptyList()
        val flags = p.requestedPermissionsFlags ?: return emptyList()
        return names.indices.filter { flags[it] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
            .map { names[it] }.sorted()
    }

    fun audit(ctx: Context): List<Map<String, Any?>> {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or
            (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        val pkgs = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        else @Suppress("DEPRECATION") pm.getInstalledPackages(flags)

        val a11yOn = enabledPackages(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val listenerOn = enabledPackages(ctx, "enabled_notification_listeners")
        val selfLaunches = try { UsageAuditor.selfLaunches(ctx) } catch (e: Exception) { android.util.Log.w("FraudShield", "usage audit failed: $e"); emptyMap() }

        // 6.4.4: forget notification counters of apps that were uninstalled
        val prefs = ctx.getSharedPreferences(AdNotificationListener.PREFS, Context.MODE_PRIVATE)
        val installed = pkgs.map { it.packageName }.toSet()
        val stale = NotifCounter.staleKeys(prefs.all.keys, installed)
        if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach { remove(it) } }.apply()

        return pkgs.filter { it.packageName != ctx.packageName }.map { p -> describe(ctx, pm, p, a11yOn, listenerOn, selfLaunches) }
    }

    /** 13.11: the same description for one freshly installed app (null when it is gone again). */
    fun auditOne(ctx: Context, pkg: String): Map<String, Any?>? {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or
            (if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        val p = try {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
            else @Suppress("DEPRECATION") pm.getPackageInfo(pkg, flags)
        } catch (e: PackageManager.NameNotFoundException) { return null }
        return describe(ctx, pm, p, enabledPackages(ctx, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            enabledPackages(ctx, "enabled_notification_listeners"), emptyMap())
    }

    private fun describe(ctx: Context, pm: PackageManager, p: android.content.pm.PackageInfo, a11yOn: Set<String>,
                         listenerOn: Set<String>, selfLaunches: Map<String, Int>): Map<String, Any?> {
        return run {
            val ai = p.applicationInfo!!
            val perms = p.requestedPermissions?.toSet() ?: emptySet()
            val isSystem = (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            val smsRequested = perms.contains(Manifest.permission.RECEIVE_SMS) || perms.contains(Manifest.permission.READ_SMS)
            mapOf(
                "packageName" to p.packageName,
                "label" to ai.loadLabel(pm).toString(),
                "notifPerHour" to AdNotificationListener.notifPerHour(ctx, p.packageName),
                "hasOverlayPermission" to overlayAllowed(ctx, pm, p.packageName, ai.uid, perms.contains(Manifest.permission.SYSTEM_ALERT_WINDOW)),
                "hasSmsPermission" to smsRequested,
                "smsGranted" to (pm.checkPermission(Manifest.permission.READ_SMS, p.packageName) == PackageManager.PERMISSION_GRANTED ||
                    pm.checkPermission(Manifest.permission.RECEIVE_SMS, p.packageName) == PackageManager.PERMISSION_GRANTED),
                "hasAccessibility" to (p.packageName in a11yOn),
                "hasNotificationListener" to (p.packageName in listenerOn),
                "hasFullScreenIntent" to perms.contains(Manifest.permission.USE_FULL_SCREEN_INTENT),
                "selfLaunchCount" to (selfLaunches[p.packageName] ?: 0),
                "certSha256" to certs(p),
                "installer" to installer(pm, p.packageName),
                "isSystem" to isSystem,
                // 13.2 inventory: what it asks for, what it got, how it was installed, how old it is
                "requestedPermissions" to perms.sorted(),
                "grantedPermissions" to granted(p),
                "targetSdk" to ai.targetSdkVersion,
                "firstInstallTime" to p.firstInstallTime,
                "lastUpdateTime" to p.lastUpdateTime,
                "versionName" to p.versionName,
                // 13.4: tracker SDK names (facts about the app, cached per version); system apps are not scanned
                "trackers" to if (isSystem) emptyList<String>() else try { TrackerScanner.scan(ctx, p.packageName).map { it.name } } catch (e: Exception) { emptyList<String>() },
                "appCategory" to (if (Build.VERSION.SDK_INT >= 26) categoryName(ai.category) else null),
            )
        }
    }
}
