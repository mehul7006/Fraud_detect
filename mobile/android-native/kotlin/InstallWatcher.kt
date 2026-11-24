package com.fraudshield.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * 13.11: a newly installed app is checked at once - inventory (13.2), tracker SDKs (13.4), server verdict (POST
 * /scan/apps: privacy, fake-app, ad-abuse) - and the user is told "Just installed X; it wants ...".
 * Android 8+ does not deliver PACKAGE_ADDED to manifest receivers, so this is registered at runtime by the
 * long-running components (VPN service, notification listener). Also cleans the firewall / reminders on uninstall.
 */
class InstallWatcher : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart ?: return
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> if (!replacing && pkg != ctx.packageName) {
                val app = ctx.applicationContext
                pool.execute { check(app, pkg) }
                pool.execute { leftoverIndex(app, pkg) } // 17.1
            }
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                AppFirewall.refresh(ctx); WarnScheduler.stop(ctx, pkg, ignore = false)
                val app = ctx.applicationContext
                pool.execute { try { LeftoverFinder.onRemoved(app, pkg) } catch (e: Exception) {} } // 17.2
            }
        }
    }

    companion object {
        private val pool = Executors.newSingleThreadExecutor()
        @Volatile private var registered: InstallWatcher? = null

        /** 17.1: remember the new app's name now (it is gone after removal) and keep the ownership index fresh. */
        private fun leftoverIndex(ctx: Context, pkg: String) {
            try {
                val pm = ctx.packageManager
                LeftoverIndex.get(ctx).putLabel(pkg, pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString())
                LeftoverIndex.get(ctx).refreshIfStale(ctx)
            } catch (e: Exception) {}
        }

        fun register(ctx: Context) {
            if (registered != null) return
            val f = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED); addDataScheme("package") }
            val r = InstallWatcher()
            try {
                if (Build.VERSION.SDK_INT >= 33) ctx.applicationContext.registerReceiver(r, f, Context.RECEIVER_EXPORTED)
                else ctx.applicationContext.registerReceiver(r, f)
                registered = r
            } catch (e: Exception) { android.util.Log.w("FraudShield", "install watcher not registered: ${e.javaClass.simpleName}") }
        }

        /** Sensitive permissions in plain words, for the alert text. */
        private val PLAIN = linkedMapOf(
            "android.permission.READ_SMS" to "your SMS", "android.permission.RECEIVE_SMS" to "your SMS",
            "android.permission.READ_CONTACTS" to "your contacts", "android.permission.READ_CALL_LOG" to "your call log",
            "android.permission.ACCESS_FINE_LOCATION" to "your location", "android.permission.RECORD_AUDIO" to "the microphone",
            "android.permission.CAMERA" to "the camera", "android.permission.SYSTEM_ALERT_WINDOW" to "to draw over other apps",
            "android.permission.BIND_ACCESSIBILITY_SERVICE" to "to control your screen", "android.permission.REQUEST_INSTALL_PACKAGES" to "to install other apps",
            "android.permission.READ_PHONE_STATE" to "your phone details")

        fun check(ctx: Context, pkg: String) {
            val app = AppAuditor.auditOne(ctx, pkg) ?: return
            if (app["isSystem"] == true) return
            val trackers = try { TrackerScanner.scan(ctx, pkg) } catch (e: Exception) { emptyList() }
            val body = app.toMutableMap().apply { put("trackers", trackers.map { it.name }) }
            val label = app["label"] as String
            val wants = (app["requestedPermissions"] as List<*>).mapNotNull { PLAIN[it] }.distinct()
            var verdict = "SAFE"
            var reasons = emptyList<String>()
            val (code, resp) = BackgroundScanner.request(ctx, "/scan/apps", JSONObject().put("apps", JSONArray().put(JSONObject(body))))
            if (code == 200 && resp != null) {
                val r = JSONObject(resp).optJSONArray("results")?.optJSONObject(0)
                if (r != null) {
                    verdict = r.optString("verdict", "SAFE")
                    val rs = r.optJSONArray("reasons")
                    reasons = (0 until (rs?.length() ?: 0)).map { rs!!.getString(it) }
                }
            }
            val sideloaded = app["installer"] !in setOf("com.android.vending", "com.xiaomi.mipicks", "com.sec.android.app.samsungapps",
                "com.huawei.appmarket", "com.oppo.market", "com.heytap.market", "com.vivo.appstore", "com.indus.appstore")
            if (verdict == "SAFE" && wants.isEmpty() && trackers.isEmpty() && !sideloaded) return // nothing worth saying
            val title = when (verdict) { "MALICIOUS" -> "Dangerous app installed: $label"; "SUSPICIOUS" -> "Check this new app: $label"; else -> "Just installed: $label" }
            val text = buildString {
                if (wants.isNotEmpty()) append("It wants ${wants.take(5).joinToString(", ")}. ")
                if (trackers.isNotEmpty()) append("Contains ${trackers.size} tracker(s): ${trackers.take(3).joinToString { it.name }}. ")
                if (sideloaded) append("Not installed from an app store. ")
                if (reasons.isNotEmpty()) append(reasons.first())
            }.trim()
            AlertNotifier.show(ctx, title, text, ("install:$pkg").hashCode())
            if (verdict != "SAFE") WarnScheduler.add(ctx, pkg, title, text) // 13.10 keep reminding until fixed
            ctx.getSharedPreferences("fraudshield_events", Context.MODE_PRIVATE) // let the app list it too
                .edit().putLong("installcheck:$pkg", System.currentTimeMillis()).apply()
        }
    }
}
