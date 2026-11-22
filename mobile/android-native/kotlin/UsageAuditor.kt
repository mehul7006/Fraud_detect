package com.fraudshield.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * 6.6: reads the last 24 h of UsageEvents (needs Usage Access, opt-in) and applies [SelfLaunchRule].
 * Returns package -> number of self-launches. Nothing but these counts is kept or sent.
 */
object UsageAuditor {
    private const val DAY = 24 * 3_600_000L

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Launcher(s), System UI, our own app: switching through these is user action, never a self-launch. */
    private fun neutralPackages(ctx: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        // Settings registers a "FallbackHome" for boot; it is not a launcher the user switches through.
        val launchers = ctx.packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }
            .filter { it != "com.android.settings" }
        return (launchers + listOf("com.android.systemui", "android", ctx.packageName)).toSet()
    }

    fun selfLaunches(ctx: Context): Map<String, Int> {
        if (!hasUsageAccess(ctx)) { android.util.Log.d("FraudShield", "usage audit: no Usage Access"); return emptyMap() }
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val it = usm.queryEvents(now - DAY, now)
        val list = mutableListOf<SelfLaunchRule.Ev>()
        val e = UsageEvents.Event()
        while (it.hasNextEvent()) {
            it.getNextEvent(e)
            val kind = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> SelfLaunchRule.Kind.RESUMED
                UsageEvents.Event.USER_INTERACTION -> SelfLaunchRule.Kind.USER_INTERACTION
                UsageEvents.Event.SCREEN_INTERACTIVE -> SelfLaunchRule.Kind.SCREEN_ON
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> SelfLaunchRule.Kind.SCREEN_OFF
                else -> null
            } ?: continue
            list.add(SelfLaunchRule.Ev(e.timeStamp, kind, e.packageName ?: ""))
        }
        val result = SelfLaunchRule.count(list, neutralPackages(ctx))
        android.util.Log.d("FraudShield", "usage audit: ${list.size} events, ${result.size} apps opened themselves") // counts only, no package names
        return result
    }
}
