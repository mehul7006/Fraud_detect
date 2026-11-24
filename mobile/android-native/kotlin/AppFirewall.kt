package com.fraudshield.app

import android.content.Context

/**
 * 13.9: "Block internet" for one app, inside the on-device VPN. The VPN answers every name the app looks up with
 * "does not exist", so it cannot reach its servers. Persisted; removed automatically when the app is uninstalled.
 * Honest limit (shown in the app): an app that uses fixed IP addresses or its own encrypted DNS is not stopped,
 * and connections it already had open stay open until they close.
 */
object AppFirewall {
    private const val PREFS = "fraudshield_firewall"
    @Volatile private var blockedUids: Set<Int> = emptySet()

    fun blocked(ctx: Context): Set<String> = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("blocked", emptySet())!!.toSet()

    fun setBlocked(ctx: Context, pkg: String, block: Boolean) {
        val next = blocked(ctx).toMutableSet().apply { if (block) add(pkg) else remove(pkg) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("blocked", next).apply()
        refresh(ctx)
        FraudVpnService.cache.clear() // cached answers must not let it through
    }

    /** UIDs of blocked packages; packages that are gone are dropped (auto-clean on uninstall). */
    fun refresh(ctx: Context) {
        val pm = ctx.packageManager
        val alive = mutableSetOf<String>()
        val uids = mutableSetOf<Int>()
        for (p in blocked(ctx)) {
            try { uids += pm.getApplicationInfo(p, 0).uid; alive += p } catch (e: Exception) { /* uninstalled */ }
        }
        if (alive.size != blocked(ctx).size) ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("blocked", alive).apply()
        blockedUids = uids
    }

    fun isBlocked(uid: Int) = uid >= 0 && uid in blockedUids
}
