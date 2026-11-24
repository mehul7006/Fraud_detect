package com.fraudshield.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build

/**
 * 12.10: why protection may not work, in a form the app can explain:
 *  OTHER_VPN   - another VPN app is connected (Android allows only one)
 *  PRIVATE_DNS - "Private DNS" with a provider name is on: lookups bypass every VPN
 *  REVOKED     - the user switched FraudShield's VPN off in Android settings / another VPN replaced it
 *  NO_CONSENT  - the system VPN prompt was not accepted yet
 */
object VpnState {
    fun check(ctx: Context): Map<String, Any?> {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val ours = FraudVpnService.isRunning()
        var otherVpn = false
        var privateDnsName: String? = null
        try {
            for (n in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(n) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) { if (!ours) otherVpn = true; continue }
                if (Build.VERSION.SDK_INT >= 28) {
                    val lp = cm.getLinkProperties(n)
                    if (lp != null && lp.isPrivateDnsActive && !lp.privateDnsServerName.isNullOrBlank()) privateDnsName = lp.privateDnsServerName
                }
            }
        } catch (e: Exception) { }
        val lastStop = FraudVpnService.prefs(ctx).getString(FraudVpnService.KEY_LAST_STOP, null)
        val state = when {
            ours && privateDnsName != null -> "PRIVATE_DNS"
            ours -> "ON"
            otherVpn -> "OTHER_VPN"
            VpnService.prepare(ctx) != null -> "NO_CONSENT"
            lastStop == "REVOKED" -> "REVOKED"
            lastStop == "NOT_ALLOWED" -> "NOT_ALLOWED"
            else -> "OFF"
        }
        return mapOf("state" to state, "otherVpn" to otherVpn, "privateDns" to privateDnsName,
            "autostartMaker" to autostartMaker())
    }

    /**
     * Phone makers whose "Autostart" switch (off by default) stops apps from starting after a reboot / update -
     * found on a real POCO (MIUI blocked MY_PACKAGE_REPLACED). Null for phones without it.
     */
    fun autostartMaker(): String? {
        val m = Build.MANUFACTURER.lowercase()
        return listOf("xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "oneplus", "iqoo", "huawei", "honor", "letv", "asus")
            .firstOrNull { m.contains(it) }
    }

    /** Opens the maker's Autostart page; falls back to the app's own settings page. */
    fun openAutostart(ctx: Context) {
        val candidates = listOf(
            android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            android.content.ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
            android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        )
        for (c in candidates) {
            try { ctx.startActivity(Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) { }
        }
        ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** 12.9: turns protection back on after a reboot / app update, if the user had it on and consent still holds. */
class VpnBootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!FraudVpnService.prefs(ctx).getBoolean(FraudVpnService.KEY_ENABLED, false)) return
        if (VpnService.prepare(ctx) != null) return // consent gone: the app will ask again
        try { FraudVpnService.start(ctx) } catch (e: Exception) { android.util.Log.w("FraudShield", "VPN restart failed: ${e.javaClass.simpleName}") }
    }
}
