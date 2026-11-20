package com.fraudshield.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import java.io.File
import java.security.MessageDigest

object DeviceInfo {
    private val REMOTE_APPS = listOf(
        "com.anydesk.anydeskandroid", "com.teamviewer.quicksupport.market", "com.teamviewer.host.market",
        "com.sand.airdroid", "com.rustdesk.rustdesk", "com.microsoft.rdc.androidx", "net.christianbeier.droidvnc_ng"
    )

    fun collect(ctx: Context): Map<String, Any?> {
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simRaw = tm.simOperator + "|" + tm.simCountryIso + "|" + tm.simState + "|" + tm.networkOperator
        val pm = ctx.packageManager
        val remote = REMOTE_APPS.filter { p -> try { pm.getPackageInfo(p, 0); true } catch (e: PackageManager.NameNotFoundException) { false } }
        val verifier = Settings.Global.getInt(ctx.contentResolver, "package_verifier_enable", 1) == 1
        return mapOf(
            "deviceId" to sha256(androidId),
            "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "osVersion" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "simHash" to sha256(simRaw),
            "fingerprint" to sha256(Build.FINGERPRINT + Build.BOARD + Build.HARDWARE + Build.MODEL),
            "rooted" to isRooted(),
            "emulator" to (Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("Emulator") || Build.HARDWARE.contains("ranchu")),
            "playProtectOn" to verifier,
            "remoteAppsInstalled" to remote
        )
    }

    private fun isRooted(): Boolean =
        Build.TAGS?.contains("test-keys") == true ||
        listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/app/Superuser.apk", "/data/adb/magisk").any { File(it).exists() }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
