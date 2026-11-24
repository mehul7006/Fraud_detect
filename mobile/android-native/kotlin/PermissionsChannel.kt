package com.fraudshield.app

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/**
 * One place for every permission FraudShield uses: status of each, and "open the exact page where the user flips it".
 * Runtime permissions are asked with the system dialog the first times; once Android stops showing the dialog
 * (denied twice / "don't ask again") the app's own permission page opens instead. Special accesses open their
 * per-app toggle page directly wherever Android has one (notification access, all-files, usage, full-screen).
 */
object PermissionsChannel {
    val METHODS = setOf("permStatus", "permOpen")
    private const val PREFS = "fraudshield_perms"

    fun handle(a: Activity, call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "permStatus" -> result.success(status(a))
            "permOpen" -> result.success(try { open(a, call.argument<String>("id") ?: "") } catch (e: Exception) { appDetails(a); "details" })
            else -> result.notImplemented()
        }
    }

    private fun granted(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun listenerOn(c: Context): Boolean {
        val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it)?.packageName == c.packageName }
    }

    private fun usageOn(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun roleHeld(c: Context) = Build.VERSION.SDK_INT >= 29 &&
        (c.getSystemService(Context.ROLE_SERVICE) as RoleManager).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    private fun maker() = Build.MANUFACTURER.lowercase()

    /** id -> {granted, required, kind}; kind tells Flutter how the opener works (dialog / page / screen). */
    fun status(c: Context): Map<String, Map<String, Any>> {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        val out = linkedMapOf<String, Map<String, Any>>()
        fun put(id: String, ok: Boolean, required: Boolean, kind: String) { out[id] = mapOf("granted" to ok, "required" to required, "kind" to kind) }
        put("notifications", if (Build.VERSION.SDK_INT >= 33) granted(c, Manifest.permission.POST_NOTIFICATIONS) else nm.areNotificationsEnabled(), true, "dialog")
        put("fullScreen", if (Build.VERSION.SDK_INT >= 34) nm.canUseFullScreenIntent() else true, true, "page")
        put("notificationAccess", listenerOn(c), true, "page")
        put("allFiles", if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else granted(c, Manifest.permission.READ_EXTERNAL_STORAGE), true, "page")
        put("phone", granted(c, Manifest.permission.READ_PHONE_STATE), true, "dialog")
        // counts only when protection is really running (consent given once but VPN off = still needs the user)
        put("vpn", VpnService.prepare(c) == null && FraudVpnService.status(c)["running"] == true, true, "screen")
        put("callScreening", roleHeld(c), false, "dialog")
        put("contacts", granted(c, Manifest.permission.READ_CONTACTS), false, "dialog")
        put("callLog", granted(c, Manifest.permission.READ_CALL_LOG), false, "dialog")
        put("usage", usageOn(c), false, "page")
        put("battery", pm.isIgnoringBatteryOptimizations(c.packageName), false, "page")
        if (maker() in setOf("xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "oneplus", "huawei", "honor")) {
            // no API can read MIUI / ColorOS autostart: shown as "check it" with a direct button, never counted as done
            out["autostart"] = mapOf("granted" to false, "required" to false, "kind" to "page", "unknown" to true)
        }
        return out
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ask with the system dialog while Android still shows it; afterwards open the app's permission page. */
    private fun askOrPage(a: Activity, perms: Array<String>, code: Int, key: String): String {
        val asked = prefs(a).getInt("asked_$key", 0)
        val canShowDialog = asked == 0 || perms.any { a.shouldShowRequestPermissionRationale(it) }
        return if (canShowDialog && asked < 2) {
            prefs(a).edit().putInt("asked_$key", asked + 1).apply()
            a.requestPermissions(perms, code); "dialog"
        } else { appPermissionsPage(a); "details" }
    }

    private fun appDetails(a: Activity) =
        a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.packageName)))

    /** The app's "Permissions" list where possible (Android 11+ has no per-permission page for third-party apps). */
    private fun appPermissionsPage(a: Activity) = try {
        a.startActivity(Intent("android.intent.action.MANAGE_APP_PERMISSIONS").putExtra("android.intent.extra.PACKAGE_NAME", a.packageName))
    } catch (e: Exception) { appDetails(a) }

    private fun tryStart(a: Activity, vararg intents: Intent): String {
        for (i in intents) try { a.startActivity(i); return i.action ?: "intent" } catch (e: Exception) { }
        appDetails(a); return "details"
    }

    fun open(a: Activity, id: String): String {
        val pkg = Uri.parse("package:" + a.packageName)
        return when (id) {
            "notifications" -> if (Build.VERSION.SDK_INT >= 33 && !granted(a, Manifest.permission.POST_NOTIFICATIONS) && prefs(a).getInt("asked_notif", 0) < 2)
                askOrPage(a, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12, "notif")
            else tryStart(a, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName))
            "fullScreen" -> if (Build.VERSION.SDK_INT >= 34) tryStart(a, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)) else "none"
            "notificationAccess" -> {
                val cn = ComponentName(a, AdNotificationListener::class.java)
                if (Build.VERSION.SDK_INT >= 30) tryStart(a,
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString()),
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                else tryStart(a, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            "allFiles" -> if (Build.VERSION.SDK_INT >= 30) tryStart(a, Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            else askOrPage(a, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 14, "files")
            "phone" -> askOrPage(a, arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE), 11, "phone")
            "contacts" -> askOrPage(a, arrayOf(Manifest.permission.READ_CONTACTS), 15, "contacts")
            "callLog" -> askOrPage(a, arrayOf(Manifest.permission.READ_CALL_LOG), 16, "calllog")
            "callScreening" -> if (Build.VERSION.SDK_INT >= 29) {
                val rm = a.getSystemService(Context.ROLE_SERVICE) as RoleManager
                if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) { a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 4716); "role" }
                else tryStart(a, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } else "none"
            "usage" -> tryStart(a, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg), Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            // the per-app battery setting lives inside App info on most phones (MIUI: "Battery saver"); list page as fallback
            "battery" -> tryStart(a, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg), Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            "autostart" -> { VpnState.openAutostart(a); "autostart" }
            else -> { appDetails(a); "details" }
        }
    }
}
