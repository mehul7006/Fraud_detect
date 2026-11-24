package com.fraudshield.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * DEBUG BUILDS ONLY: lets automated device tests (adb) switch the settings a user would tap, because MIUI blocks
 * injected taps. Protected by android.permission.DUMP in the manifest (only the adb shell / system hold it) and a
 * no-op in release builds. Output goes to logcat tag FraudShieldDebug: counts and masked values only, never a full
 * phone number.
 *
 *   adb shell am broadcast -n com.fraudshield.app/.DebugCommandReceiver --es cmd <command> [--es arg <value>] [--ez on true|false]
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        val cmd = intent.getStringExtra("cmd") ?: return
        val arg = intent.getStringExtra("arg") ?: ""
        val on = intent.getBooleanExtra("on", true)
        val pending = goAsync()
        Thread {
            val out = try { run(ctx.applicationContext, cmd, arg, on) } catch (e: Throwable) { "ERROR ${e.javaClass.simpleName}: ${e.message?.take(120)}" }
            Log.i(TAG, "$cmd -> $out")
            pending.finish()
        }.start()
    }

    private fun mask(n: String?) = if (n == null) "null" else n.take(3) + "…" + n.takeLast(2)

    private fun run(ctx: Context, cmd: String, arg: String, on: Boolean): String = when (cmd) {
        // 12.7 local-only: no server checks, list only
        "localOnly" -> {
            FraudVpnService.prefs(ctx).edit().putBoolean("local_only", on).apply()
            FraudVpnService.applyOptions(ctx); "localOnly=${FraudVpnService.guard.localOnly}"
        }
        // 13.9 block internet for one app (per-UID NXDOMAIN)
        "firewall" -> { AppFirewall.setBlocked(ctx, arg, on); "blocked=${AppFirewall.blocked(ctx)}" }
        // 16.x call guard
        "callMode" -> { CallGuardStore.setMode(ctx, arg.toInt()); CallGuardStore.load(ctx); "mode=${CallGuardStore.mode(ctx)}" }
        "callBlock" -> { val c = CallGuardStore.setBlocked(ctx, arg, on); CallGuardStore.load(ctx); "number=${mask(c)} blocked=$on blockList=${CallGuardStore.block(ctx).size}" }
        "callAllow" -> { val c = CallGuardStore.setAllowed(ctx, arg, on); CallGuardStore.load(ctx); "number=${mask(c)} allowed=$on allowList=${CallGuardStore.allow(ctx).size}" }
        "callSync" -> { val r = CallGuardStore.sync(ctx); CallGuardStore.load(ctx); "sync=$r" }
        "callState" -> { val l = CallGuardStore.lists(ctx); "mode=${l.mode} allow=${l.allow.size} block=${l.block.size}" }
        // 18.4 / 18.5 blocking
        "blockPause" -> { AdBlocker.init(ctx, BuildConfig.FS_FLAVOUR); AdBlocker.pause(ctx, arg.toIntOrNull() ?: 0); "pausedUntil=${AdBlocker.policy.pausedUntil}" }
        "blockAllowApp" -> { AdBlocker.init(ctx, BuildConfig.FS_FLAVOUR); AdBlocker.allowApp(ctx, arg, on); FraudVpnService.cache.clear(); "allowedApps=${AdBlocker.allowedApps(ctx)}" }
        "blockAllowSite" -> { AdBlocker.init(ctx, BuildConfig.FS_FLAVOUR); AdBlocker.allowSite(ctx, arg, on); FraudVpnService.cache.clear(); "allowedSites=${AdBlocker.policy.allowedSites}" }
        "blockStats" -> {
            AdBlocker.init(ctx, BuildConfig.FS_FLAVOUR); BlockStats.get(ctx).flush()
            val s = BlockStats.get(ctx).summary(arg.toIntOrNull() ?: 1)
            "total=${s["total"]} byCategory=${s["byCategory"]} top=${(s["topDomains"] as List<*>).take(5)} apps=${(s["byApp"] as List<*>).size}"
        }
        "blockSettings" -> { AdBlocker.init(ctx, BuildConfig.FS_FLAVOUR); AdBlocker.settings(ctx).filterKeys { it != "brokeReports" }.toString() }
        // 17.x leftovers
        "leftoverScan" -> OrphanScanner.scan(ctx).joinToString(prefix = "orphans=[", postfix = "]") { "${it["rel"]} (${it["files"]} files, ${it["bytes"]} B, ${it["app"]}, ticked=${it["selected"] ?: it["ticked"]})" }
        "leftoverApks" -> LeftoverFinder.oldApks(ctx, emptySet()).joinToString(prefix = "apks=[", postfix = "]") { "${it["rel"] ?: it["path"]}: ${it["status"]} ticked=${it["selected"] ?: it["ticked"]}" }
        "leftoverCheck" -> SettingsResidueCheck.check(ctx, arg).joinToString { "${it.kind}=${it.state}" }
        else -> "unknown command"
    }

    companion object { const val TAG = "FraudShieldDebug" }
}
