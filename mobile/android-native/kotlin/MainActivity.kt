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
    private var sharedText: String? = null

    // 4.8: a link shared from the browser (Share -> FraudShield) arrives as ACTION_SEND text/plain
    private fun captureShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type == "text/plain") {
            sharedText = i.getStringExtra(Intent.EXTRA_TEXT)
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        captureShare(intent)
    }

    // 7.11: while FraudShield is on screen the Flutter side handles links (in-app alert); otherwise the listener does
    override fun onResume() { super.onResume(); BackgroundSession.appInForeground = true }
    override fun onPause() { BackgroundSession.appInForeground = false; super.onPause() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureShare(intent)
    }

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
                    "hasAllFilesAccess" -> result.success(hasStorageAccess())
                    "openAllFilesAccessSettings" -> { requestStorageAccess(); result.success(true) }
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
                    "forwardingIndicator" -> CallForwardIndicator.read(this) { result.success(it) }
                    "disableCallForwarding" ->CallForwardChecker.run(this, "##002#") { result.success(it) }
                    "dialCode" -> {
                        val code = call.argument<String>("code") ?: ""
                        if (!allowedUssd(code)) { result.error("BAD_CODE", "USSD code not allowed", null) }
                        else {
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code))))
                            result.success(true)
                        }
                    }
                    "auditApps" -> Thread {
                        // reading packages + usage events can take a second; keep the UI thread free
                        val r = try { AppAuditor.audit(this) } catch (e: Exception) { null }
                        runOnUiThread { if (r != null) result.success(r) else result.error("AUDIT_FAILED", "app audit failed", null) }
                    }.start()
                    "hasUsageAccess" -> result.success(UsageAuditor.hasUsageAccess(this))
                    "openUsageAccessSettings" -> { UsageAuditor.openUsageAccessSettings(this); result.success(true) }
                    "usageAudit" -> Thread {
                        val r = try { UsageAuditor.selfLaunches(this) } catch (e: Exception) { emptyMap<String, Int>() }
                        runOnUiThread { result.success(r) }
                    }.start()
                    "isInstalled" -> result.success(try {
                        packageManager.getPackageInfo(call.argument<String>("package") ?: "", 0); true
                    } catch (e: Exception) { false })
                    "setBackgroundSession" -> {
                        BackgroundSession.save(this, call.argument<String>("apiBase") ?: "", call.argument<String>("token"),
                            call.argument<String>("refresh"),
                            call.argument<List<String>>("disabled") ?: emptyList(), call.argument<List<String>>("trusted") ?: emptyList())
                        result.success(true)
                    }
                    "clearBackgroundSession" -> { BackgroundSession.clear(this); result.success(true) }
                    "takeSharedText" ->{ val t = sharedText; sharedText = null; result.success(t) }
                    "takePendingEvents" -> result.success(AdNotificationListener.takePending(this))
                    "scanNewFiles" -> Thread {
                        // hashing big files must not block the UI thread (5.4.3)
                        val r = try { FileScanner.scanNew(this) } catch (e: Exception) { null }
                        runOnUiThread { if (r != null) result.success(r) else result.error("SCAN_FAILED", "file scan failed", null) }
                    }.start()
                    "deleteFile" -> result.success(FileScanner.delete(call.argument<String>("path") ?: ""))
                    "uninstallApp" -> {
                        val pkg = call.argument<String>("package") ?: ""
                        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
                        result.success(true)
                    }
                    "openUrl" -> {
                        val u = call.argument<String>("url") ?: ""
                        if (!u.startsWith("https://")) result.success(false) // never open other schemes from Dart
                        else try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))); result.success(true) }
                        catch (e: Exception) { result.success(false) }
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

    // 5.8: storage permission per Android version, see StorageAccess
    @Suppress("DEPRECATION")
    private val legacyStorage = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private fun hasStorageAccess(): Boolean = StorageAccess.hasAccess(
        Build.VERSION.SDK_INT,
        Build.VERSION.SDK_INT >= StorageAccess.ALL_FILES_SDK && Environment.isExternalStorageManager(),
        checkSelfPermission(legacyStorage[0]) == PackageManager.PERMISSION_GRANTED,
        checkSelfPermission(legacyStorage[1]) == PackageManager.PERMISSION_GRANTED)

    private var storageAskedAt = 0L

    private fun requestStorageAccess() {
        when (StorageAccess.request(Build.VERSION.SDK_INT)) {
            StorageAccess.Request.ALL_FILES_SETTINGS -> startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            StorageAccess.Request.RUNTIME_PROMPT -> {
                storageAskedAt = android.os.SystemClock.elapsedRealtime()
                requestPermissions(legacyStorage, STORAGE_REQUEST)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != STORAGE_REQUEST) return
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        val elapsed = android.os.SystemClock.elapsedRealtime() - storageAskedAt
        if (StorageAccess.openAppSettingsInstead(granted, elapsed)) startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private companion object { const val STORAGE_REQUEST = 13 }

    /** Only MMI codes for querying/disabling forwarding. Prevents the bridge from dialing arbitrary USSD. */
    private fun allowedUssd(code: String) = code in setOf("*#21#", "*#67#", "*#61#", "*#62#", "*#002#", "##002#")
}
