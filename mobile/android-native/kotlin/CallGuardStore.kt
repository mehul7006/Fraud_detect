package com.fraudshield.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Phase 16 on-phone state: mode, allow / block lists (canonical numbers, kept on the phone only), the signed scam list
 * (hashes, 16.4) and the cached pattern rules (16.3). Everything the call decision needs is local, so screening works
 * offline; sync only refreshes the lists.
 */
object CallGuardStore {
    private const val PREFS = "fs_callguard"
    private const val STALE_MS = 12L * 3600_000

    val scam = ScamNumberList()
    @Volatile var rules: NumberRules = NumberRules.DEFAULT; private set
    @Volatile private var loaded = false

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun listFile(ctx: Context) = File(ctx.filesDir, "call-numbers.txt")

    @Synchronized fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            prefs(ctx).getString("rules_json", null)?.let { rules = parseRules(JSONObject(it)) }
        } catch (e: Exception) { rules = NumberRules.DEFAULT }
        try {
            val f = listFile(ctx)
            if (f.exists()) {
                val lines = f.readLines()
                val v = lines.firstOrNull()?.toLongOrNull()
                if (v != null) scam.restore(v, lines.drop(1).mapNotNull { l ->
                    val i = l.indexOf(':'); if (i == 64) l.substring(0, 64) to l.substring(65) else null }.toMap())
            }
        } catch (e: Exception) { /* list rebuilt on next sync */ }
    }

    fun mode(ctx: Context) = CallGuardEngine.Mode.of(prefs(ctx).getInt("mode", CallGuardEngine.Mode.BLOCK_SCAM.n))
    fun setMode(ctx: Context, n: Int) = prefs(ctx).edit().putInt("mode", CallGuardEngine.Mode.of(n).n).apply()

    fun allow(ctx: Context): Set<String> = prefs(ctx).getStringSet("allow", emptySet())!!.toSet()
    fun block(ctx: Context): Set<String> = prefs(ctx).getStringSet("block", emptySet())!!.toSet()

    /** @return the canonical number stored, or null when the text is not a phone number. */
    @Synchronized fun setAllowed(ctx: Context, raw: String, allowed: Boolean): String? {
        val c = CallNumbers.canonical(raw) ?: return null
        val a = allow(ctx).toMutableSet(); val b = block(ctx).toMutableSet()
        if (allowed) { a += c; b -= c } else a -= c
        prefs(ctx).edit().putStringSet("allow", a).putStringSet("block", b).apply()
        return c
    }

    /** "Block number". 1600 / 140 numbers are refused (TRAI): returns null. */
    @Synchronized fun setBlocked(ctx: Context, raw: String, blocked: Boolean): String? {
        val c = CallNumbers.canonical(raw) ?: return null
        if (blocked && rules.classify(c)?.action == "NEVER_BLOCK") return null
        val a = allow(ctx).toMutableSet(); val b = block(ctx).toMutableSet()
        if (blocked) { b += c; a -= c } else b -= c
        prefs(ctx).edit().putStringSet("allow", a).putStringSet("block", b).apply()
        return c
    }

    fun lists(ctx: Context): CallGuardEngine.Lists {
        load(ctx)
        return CallGuardEngine.Lists(mode(ctx), allow(ctx), block(ctx), scam, rules)
    }

    // ---------------- sync (16.3 rules, 16.4 signed scam list)
    fun syncIfStale(ctx: Context) {
        val p = prefs(ctx)
        if (System.currentTimeMillis() - p.getLong("synced", 0) < STALE_MS) return
        sync(ctx)
    }

    /** @return {"rules": ok, "numbers": ok} */
    fun sync(ctx: Context): Map<String, Boolean> {
        load(ctx)
        val r = syncRules(ctx)
        val n = syncNumbers(ctx)
        if (r && n) prefs(ctx).edit().putLong("synced", System.currentTimeMillis()).apply()
        return mapOf("rules" to r, "numbers" to n)
    }

    private fun syncRules(ctx: Context): Boolean = try {
        val (code, body) = BackgroundScanner.request(ctx, "/config/number-rules", null)
        if (code != 200 || body == null) false else {
            rules = parseRules(JSONObject(body))
            prefs(ctx).edit().putString("rules_json", body).apply()
            true
        }
    } catch (e: Exception) { false }

    private fun syncNumbers(ctx: Context): Boolean = try {
        val (code, body) = BackgroundScanner.request(ctx, "/config/numbers?since=${scam.version}", null)
        if (code != 200 || body == null) false else {
            val j = JSONObject(body)
            val added = j.optJSONArray("added") ?: JSONArray()
            val removed = j.optJSONArray("removed") ?: JSONArray()
            val ok = scam.applyDelta(j.getLong("version"), j.getBoolean("full"),
                (0 until added.length()).map { added.getJSONObject(it).let { e -> e.getString("hash") to e.getString("category") } },
                (0 until removed.length()).map { removed.getString(it) }, j.getString("signature"))
            if (!ok) android.util.Log.w("FraudShieldCall", "number list signature invalid - ignored")
            else listFile(ctx).writeText(scam.version.toString() + "\n" + scam.snapshot().entries.joinToString("\n") { "${it.key}:${it.value}" })
            ok
        }
    } catch (e: Exception) { false }

    fun parseRules(j: JSONObject): NumberRules {
        val arr = j.getJSONArray("rules")
        val list = (0 until arr.length()).map { i ->
            val r = arr.getJSONObject(i)
            val label = r.optJSONObject("label") ?: JSONObject()
            fun int(k: String) = if (r.has(k)) r.getInt(k) else null
            fun str(k: String) = if (r.has(k)) r.getString(k) else null
            NumberRule(r.getString("id"), r.getString("type"), r.optString("category"), r.optString("action", "LABEL"),
                label.optString("en"), label.optString("hi", label.optString("en")),
                str("prefix"), int("length"), str("countryCode"), int("notLength"), int("minDigits"), int("maxDigits"))
        }
        val codes = j.optJSONArray("riskyCountryCodes") ?: JSONArray()
        return NumberRules(j.optString("version", "server"), list, (0 until codes.length()).map { codes.getString(it) })
    }

    /** 16.5: the user tapped Report. The only time a number leaves the phone. @return HTTP status (0 = offline). */
    fun report(ctx: Context, raw: String, category: String): Int =
        BackgroundScanner.request(ctx, "/numbers/report", JSONObject().put("number", raw).put("category", category)).first
}
