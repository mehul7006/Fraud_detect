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

    fun audit(ctx: Context): List<Map<String, Any>> {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
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

        return pkgs.filter { it.packageName != ctx.packageName }.map { p ->
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
                "isSystem" to isSystem
            )
        }
    }
}
