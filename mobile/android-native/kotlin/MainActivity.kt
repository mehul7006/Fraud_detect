package com.fraudshield.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.telephony.TelephonyManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/** Bridge: Flutter <-> native protection features. Channel name must match lib/core/native_bridge.dart */
class MainActivity : FlutterActivity() {
    private val channel = "com.fraudshield.app/native"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channel).setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "getDeviceInfo" -> result.success(DeviceInfo.collect(this))
                    "hasNotificationAccess" -> result.success(AdNotificationListener.isEnabled(this))
                    "openNotificationAccessSettings" -> {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); result.success(true)
                    }
                    "hasAllFilesAccess" -> result.success(
                        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true)
                    "openAllFilesAccessSettings" -> {
                        if (Build.VERSION.SDK_INT >= 30) startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
                        result.success(true)
                    }
                    "requestPhonePermission" -> {
                        requestPermissions(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE), 11)
                        result.success(true)
                    }
                    "hasPhonePermission" -> result.success(
                        checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
                    "hasNotificationPermission" -> result.success(
                        Build.VERSION.SDK_INT < 33 ||
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                    "requestNotificationPermission" -> {
                        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
                        result.success(true)
                    }
                    "checkCallForwarding" -> {
                        val code = call.argument<String>("code") ?: "*#21#"
                        if (!allowedUssd(code)) { result.error("BAD_CODE", "USSD code not allowed", null) }
                        else CallForwardChecker.run(this, code) { result.success(it) }
                    }
                    "disableCallForwarding" -> CallForwardChecker.run(this, "##002#") { result.success(it) }
                    "dialCode" -> {
                        val code = call.argument<String>("code") ?: ""
                        if (!allowedUssd(code)) { result.error("BAD_CODE", "USSD code not allowed", null) }
                        else {
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))))
                            result.success(true)
                        }
                    }
                    "auditApps" -> result.success(AppAuditor.audit(this))
                    "takePendingEvents" -> result.success(AdNotificationListener.takePending(this))
                    "scanNewFiles" -> result.success(FileScanner.scanNew(this))
                    "deleteFile" -> result.success(FileScanner.delete(call.argument<String>("path") ?: ""))
                    "uninstallApp" -> {
                        val pkg = call.argument<String>("package") ?: ""
                        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
                        result.success(true)
                    }
                    "openApp" -> {
                        val pkg = call.argument<String>("package") ?: ""
                        val i = packageManager.getLaunchIntentForPackage(pkg)
                        if (i != null) { startActivity(i); result.success(true) } else result.success(false)
                    }
                    "showAlert" -> {
                        AlertNotifier.show(this, call.argument<String>("title") ?: "FraudShield",
                            call.argument<String>("body") ?: "", call.argument<Int>("id") ?: 1)
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            } catch (e: Exception) {
                result.error("NATIVE_ERROR", e.message, null)
            }
        }
    }

    /** Only MMI codes for querying/disabling forwarding. Prevents the bridge from dialing arbitrary USSD. */
    private fun allowedUssd(code: String) = code in setOf("*#21#", "*#67#", "*#61#", "*#62#", "*#002#", "##002#")
}
