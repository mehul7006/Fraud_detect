package com.fraudshield.app

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

object AppAuditor {
    private const val ACCESSIBILITY = "android.permission.BIND_ACCESSIBILITY_SERVICE"
    private const val NOTIF_LISTENER = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"

    /** Output matches backend AppInfo DTO. Requires QUERY_ALL_PACKAGES. */
    fun audit(ctx: Context): List<Map<String, Any>> {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        val pkgs = if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        else @Suppress("DEPRECATION") pm.getInstalledPackages(flags)

        return pkgs.filter { it.packageName != ctx.packageName }.map { p ->
            val perms = p.requestedPermissions?.toSet() ?: emptySet()
            val isSystem = (p.applicationInfo!!.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            mapOf(
                "packageName" to p.packageName,
                "label" to p.applicationInfo!!.loadLabel(pm).toString(),
                "notifPerHour" to AdNotificationListener.notifPerHour(ctx, p.packageName),
                "hasOverlayPermission" to perms.contains(Manifest.permission.SYSTEM_ALERT_WINDOW),
                "hasSmsPermission" to (perms.contains(Manifest.permission.RECEIVE_SMS) || perms.contains(Manifest.permission.READ_SMS)),
                "hasAccessibility" to (p.services?.any { it.permission == ACCESSIBILITY } ?: false),
                "hasNotificationListener" to (p.services?.any { it.permission == NOTIF_LISTENER } ?: false),
                "hasFullScreenIntent" to perms.contains(Manifest.permission.USE_FULL_SCREEN_INTENT),
                "isSystem" to isSystem
            )
        }
    }
}
