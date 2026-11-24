package com.fraudshield.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 18.3 / 18.4: phone-wide ad / tracker / malware blocking inside the DNS VPN (Phase 12).
 *
 * [BlockPolicy] is the pure decision (unit-tested); [AdBlocker] keeps its settings, loads the signed list and syncs it.
 * Flavours (18.9): the Google Play build ("play") has NO ads category at all - Play policy forbids blocking or
 * interfering with other apps' ads (docs/ad-blocking-policy.md); it downloads the trackers + malware list only.
 * The website build ("web") adds ads.
 *
 * Escape hatches: allow an app, allow a site, pause for N minutes, "this app broke" (= allow the app + keep a
 * local note; nothing is sent). Blocked lookups get "does not exist" silently; Phase 12's scam alerts are separate.
 */
class BlockPolicy(val adsAvailable: Boolean) {
    @Volatile var list: BlockList = BlockList.EMPTY
    @Volatile var enabled: Set<Int> = defaults(adsAvailable)
    @Volatile var pausedUntil = 0L
    @Volatile var allowedUids: Set<Int> = emptySet()
    @Volatile var allowedSites: Set<String> = emptySet()

    /** Category that blocks this lookup (BlockList.ADS / TRACKERS / MALWARE), or 0 = let it through. */
    fun decide(name: String, uid: Int, now: Long): Int {
        if (now < pausedUntil) return 0
        if (uid >= 0 && uid in allowedUids) return 0
        val n = name.lowercase().trimEnd('.')
        val sites = allowedSites
        if (sites.isNotEmpty() && DomainGuard.parents(n).any { it in sites }) return 0
        val c = list.category(n)
        if (c == 0 || c !in enabled) return 0
        if (c == BlockList.ADS && !adsAvailable) return 0
        return c
    }

    companion object {
        fun defaults(adsAvailable: Boolean) =
            if (adsAvailable) setOf(BlockList.ADS, BlockList.TRACKERS, BlockList.MALWARE) else setOf(BlockList.TRACKERS, BlockList.MALWARE)
    }
}

object AdBlocker {
    private const val PREFS = "fraudshield_block"
    private const val FILE = "blocklists.bin"

    /** 18.9: set once from BuildConfig.FS_FLAVOUR ("play" | "web"); the safe default is the Play behaviour. */
    @Volatile var flavour = "play"; private set
    @Volatile var policy = BlockPolicy(adsAvailable = false); private set
    val variant get() = if (flavour == "web") "web" else "play"

    @Volatile private var ready = false

    /** Idempotent: the VPN service and the app screens both call it. */
    @Synchronized fun init(ctx: Context, flavour: String) {
        val f = if (flavour == "web") "web" else "play"
        if (ready && this.flavour == f) return
        this.flavour = f
        policy = BlockPolicy(adsAvailable = f == "web")
        loadSettings(ctx)
        loadList(ctx)
        ready = true
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- settings
    fun loadSettings(ctx: Context) {
        val p = prefs(ctx)
        val cats = p.getStringSet("categories", null)?.mapNotNull { n -> BlockList.NAMES.entries.firstOrNull { it.value == n }?.key }?.toSet()
        policy.enabled = (cats ?: BlockPolicy.defaults(policy.adsAvailable)).filter { it != BlockList.ADS || policy.adsAvailable }.toSet()
        policy.pausedUntil = p.getLong("paused_until", 0)
        policy.allowedSites = p.getStringSet("allow_sites", emptySet())!!.toSet()
        refreshApps(ctx)
    }

    fun setCategory(ctx: Context, name: String, on: Boolean) {
        val c = BlockList.NAMES.entries.firstOrNull { it.value == name }?.key ?: return
        if (c == BlockList.ADS && !policy.adsAvailable) return // Play build: no ads switch, also not via the channel
        val next = policy.enabled.toMutableSet().apply { if (on) add(c) else remove(c) }
        policy.enabled = next
        prefs(ctx).edit().putStringSet("categories", next.mapNotNull { BlockList.NAMES[it] }.toSet()).apply()
    }

    fun pause(ctx: Context, minutes: Int) {
        val until = if (minutes <= 0) 0 else System.currentTimeMillis() + minutes * 60_000L
        policy.pausedUntil = until
        prefs(ctx).edit().putLong("paused_until", until).apply()
    }

    fun allowedApps(ctx: Context): Set<String> = prefs(ctx).getStringSet("allow_apps", emptySet())!!.toSet()

    fun allowApp(ctx: Context, pkg: String, allow: Boolean) {
        val next = allowedApps(ctx).toMutableSet().apply { if (allow) add(pkg) else remove(pkg) }
        prefs(ctx).edit().putStringSet("allow_apps", next).apply()
        refreshApps(ctx)
    }

    fun allowSite(ctx: Context, domain: String, allow: Boolean) {
        val d = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/').trimEnd('.')
        if (!d.contains('.')) return
        val next = policy.allowedSites.toMutableSet().apply { if (allow) add(d) else remove(d) }
        policy.allowedSites = next
        prefs(ctx).edit().putStringSet("allow_sites", next).apply()
    }

    /** "This app broke": allow it at once and keep a local note (shown in the app, never sent anywhere). */
    fun appBroke(ctx: Context, pkg: String) {
        allowApp(ctx, pkg, true)
        val p = prefs(ctx)
        val arr = JSONArray(p.getString("broke_reports", "[]"))
        val next = JSONArray().put(JSONObject().put("package", pkg).put("at", System.currentTimeMillis()))
        for (i in 0 until minOf(arr.length(), 49)) next.put(arr.get(i))
        p.edit().putString("broke_reports", next.toString()).apply()
    }

    /** Allowed packages -> UIDs; uninstalled apps are dropped (like AppFirewall). */
    private fun refreshApps(ctx: Context) {
        val pm = ctx.packageManager
        val alive = mutableSetOf<String>(); val uids = mutableSetOf<Int>()
        for (pkg in allowedApps(ctx)) try { uids += pm.getApplicationInfo(pkg, 0).uid; alive += pkg } catch (_: Exception) { }
        if (alive.size != allowedApps(ctx).size) prefs(ctx).edit().putStringSet("allow_apps", alive).apply()
        policy.allowedUids = uids
    }

    fun settings(ctx: Context): Map<String, Any?> {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        return mapOf(
            "flavour" to flavour, "adsAvailable" to policy.adsAvailable,
            "categories" to policy.enabled.mapNotNull { BlockList.NAMES[it] },
            "pausedUntil" to (if (policy.pausedUntil > now) policy.pausedUntil else 0L),
            "allowedApps" to allowedApps(ctx).toList(), "allowedSites" to policy.allowedSites.toList(),
            "brokeReports" to p.getString("broke_reports", "[]"),
            "listVersion" to policy.list.version, "listSize" to policy.list.size, "listCounts" to policy.list.counts(),
            "listSynced" to p.getLong("list_synced", 0),
        )
    }

    // ---- the list: signed file from GET /config/blocklists (18.2)
    fun loadList(ctx: Context) {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return
        try { policy.list = f.inputStream().use(BlockList::read) } catch (e: Exception) {
            android.util.Log.w("FraudShield", "block list unreadable - ignored: ${e.javaClass.simpleName}"); f.delete()
        }
    }

    /** Downloads the flavour's list when the server has a newer version; signature + SHA-256 checked first. */
    fun sync(ctx: Context): Boolean {
        val (code, body) = BackgroundScanner.request(ctx, "/config/blocklists?variant=$variant", null) // also renews the token
        if (code != 200 || body == null) return false
        val m = JSONObject(body)
        val version = m.optLong("version"); val count = m.optInt("count"); val sha = m.optString("sha256")
        if (count == 0 || version == policy.list.version) return true
        if (!DomainGuard.verify(BlockList.canonical(variant, version, count, sha), m.optString("signature"), DomainGuard.BLOCKLIST_PUBLIC_KEY)) {
            android.util.Log.w("FraudShield", "block list signature invalid - ignored"); return false
        }
        val bytes = download(ctx, "/config/blocklists/file?variant=$variant") ?: return false
        if (BlockList.sha256Hex(bytes) != sha) { android.util.Log.w("FraudShield", "block list file does not match its signature"); return false }
        val list = try { BlockList.read(bytes.inputStream()) } catch (e: Exception) { return false }
        if (list.version != version || list.size != count) return false
        if (!policy.adsAvailable && list.counts()["ads"] != 0) return false // never ads in the Play build
        val f = File(ctx.filesDir, FILE)
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        policy.list = list
        prefs(ctx).edit().putLong("list_synced", System.currentTimeMillis()).apply()
        return true
    }

    private fun download(ctx: Context, path: String): ByteArray? = try {
        val s = BackgroundSession.load(ctx) ?: return null
        val c = URL(s.apiBase.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 60_000
        c.setRequestProperty("Authorization", "Bearer ${s.token}")
        val b = if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        c.disconnect()
        b?.takeIf { it.size <= 64 shl 20 }
    } catch (e: Exception) {
        android.util.Log.w("FraudShield", "block list download failed: ${e.javaClass.simpleName}"); null
    }
}
