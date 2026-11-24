package com.fraudshield.app

import android.content.Context
import org.json.JSONObject

/**
 * 13.5: what an app's network use says about it, judged ON THE PHONE from the VPN's lookup log (12.8) and the
 * signed domain-category list (GET /config/domains, 13.6). The domains themselves never leave the phone.
 * Findings: many ad / tracking servers, any known malware host.
 */
object AppNetworkAnalyzer {
    const val MANY_ADS = 10

    data class Result(val ads: Int, val trackers: Int, val malware: Int, val total: Int, val codes: List<String>,
                      val byDomain: Map<String, String?>)

    /** Pure (unit-tested): [domains] = domain -> lookups; [category] = domain category lookup (parents included). */
    fun analyze(domains: Map<String, Int>, category: (String) -> String?): Result {
        val cats = domains.keys.associateWith { category(it) }
        val ads = cats.values.count { it == "ads" }
        val trk = cats.values.count { it == "analytics" || it == "tracker" }
        val mal = cats.values.count { it == "malware" }
        val codes = buildList {
            if (mal > 0) add("MALWARE_DOMAIN")
            if (ads + trk >= MANY_ADS) add("MANY_AD_DOMAINS")
        }
        return Result(ads, trk, mal, domains.size, codes, cats)
    }

    // ---- the category list (signed deltas like the blocklist; same public key)
    @Volatile private var map: Map<String, String> = emptyMap()
    @Volatile private var version = 0L

    fun categoryOf(domain: String): String? {
        val m = map
        for (p in DomainGuard.parents(domain.lowercase().trimEnd('.'))) m[p]?.let { return it }
        return null
    }

    fun canonical(version: Long, full: Boolean, added: List<Pair<String, String>>, removed: List<String>) =
        "fraudshield-domains-v1\n$version\n$full\n${added.joinToString(",") { "${it.first}:${it.second}" }}\n${removed.joinToString(",")}"

    private fun file(ctx: Context) = java.io.File(ctx.filesDir, "domain-categories.txt")

    fun load(ctx: Context) {
        if (version > 0 || !file(ctx).exists()) return
        val lines = file(ctx).readLines()
        version = lines.firstOrNull()?.toLongOrNull() ?: return
        map = lines.drop(1).mapNotNull { l -> l.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    }

    fun sync(ctx: Context): Boolean {
        load(ctx)
        val (code, body) = BackgroundScanner.request(ctx, "/config/domains?since=$version", null)
        if (code != 200 || body == null) return false
        val j = JSONObject(body)
        val a = j.optJSONArray("added"); val r = j.optJSONArray("removed")
        val added = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it).let { o -> o.getString("domain") to o.getString("category") } }
        val removed = (0 until (r?.length() ?: 0)).map { r!!.getString(it) }
        val v = j.getLong("version"); val full = j.getBoolean("full")
        if (!DomainGuard.verify(canonical(v, full, added, removed), j.getString("signature"), DomainGuard.BLOCKLIST_PUBLIC_KEY)) {
            android.util.Log.w("FraudShield", "domain list signature invalid - ignored"); return false
        }
        val next = if (full) HashMap<String, String>() else HashMap(map)
        removed.forEach { next.remove(it) }; added.forEach { next[it.first] = it.second }
        map = next; version = v
        file(ctx).writeText(v.toString() + "\n" + next.entries.joinToString("\n") { "${it.key}:${it.value}" })
        return true
    }

    fun size() = map.size

    /** For the app detail screen: last 7 days of one app. */
    fun forApp(ctx: Context, pkg: String): Map<String, Any?> {
        load(ctx)
        val d = TrafficStore.get(ctx).domainsOf(pkg, 7).associate { it["domain"] as String to it["count"] as Int }
        val r = analyze(d, ::categoryOf)
        return mapOf("ads" to r.ads, "trackers" to r.trackers, "malware" to r.malware, "total" to r.total, "codes" to r.codes,
            "domains" to d.entries.sortedByDescending { it.value }.take(50).map { mapOf("domain" to it.key, "count" to it.value, "category" to r.byDomain[it.key]) })
    }
}
