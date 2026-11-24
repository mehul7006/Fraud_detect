package com.fraudshield.app

/**
 * Phase 16: the phone's copy of the server's number rules (backend calls/PhoneNumbers.java). Must stay identical:
 *  - canonical = E.164 text ("+919876543210"); spaces, '-', '.', '(', ')' dropped; "00" -> "+"; a bare 10-digit number,
 *    "0" + 10 digits and "91" + 10 digits are Indian (+91). Anything else without '+' (short codes such as 1930, odd
 *    lengths) has no canonical form (null). A '+' number has 8..15 digits, no leading 0; +91 needs exactly 10 digits.
 *  - hash = SHA-256 of the canonical text (UTF-8), lower-case hex. prefix = first 5 hex chars.
 * Pure Kotlin (no Android types): tested on the JVM against the server's own vectors (CallGuardEngineTest).
 */
object CallNumbers {
    private val JUNK = Regex("[\\s\\-.()]")
    private val DIGITS = Regex("\\d+")
    private val PLUS_DIGITS = Regex("\\d{8,15}")

    /** The number as the rules see it: separators removed, leading "00" -> "+". */
    fun cleaned(raw: String?): String? {
        if (raw == null) return null
        var s = JUNK.replace(raw.trim(), "")
        if (s.startsWith("00")) s = "+" + s.substring(2)
        return s
    }

    fun canonical(raw: String?): String? {
        val s = cleaned(raw) ?: return null
        val d: String
        if (s.startsWith("+")) {
            d = s.substring(1)
            if (!PLUS_DIGITS.matches(d)) return null
        } else {
            if (!DIGITS.matches(s)) return null
            d = when {
                s.length == 10 -> "91$s"
                s.length == 11 && s.startsWith("0") -> "91" + s.substring(1)
                s.length == 12 && s.startsWith("91") -> s
                else -> return null
            }
        }
        if (d.startsWith("91") && d.length != 12) return null
        if (d.startsWith("0")) return null
        return "+$d"
    }

    fun hash(canonical: String): String = DomainGuard.sha256(canonical)

    fun prefix(hash: String) = hash.substring(0, 5)

    /** The 10 national digits of an Indian number, else null. */
    fun indianNational(canonical: String?): String? =
        if (canonical != null && canonical.startsWith("+91") && canonical.length == 13) canonical.substring(3) else null

    /** Digits only, no '+', and no canonical form: 112, 1930, 1800 toll-free, bank short codes - never blocked. */
    fun isShortOrService(raw: String?): Boolean {
        val s = cleaned(raw) ?: return false
        return s.isNotEmpty() && DIGITS.matches(s) && canonical(s) == null
    }

    /** Country calling code of a '+' number (ITU E.164 zone rules: 1 and 7 are one digit, a fixed set is two, rest three). */
    fun countryCode(cleaned: String): String? {
        if (!cleaned.startsWith("+") || cleaned.length < 3) return null
        val d = cleaned.substring(1)
        if (d[0] == '1' || d[0] == '7') return "+" + d[0]
        val two = d.take(2)
        return if (two in TWO_DIGIT) "+$two" else "+" + d.take(3)
    }

    private val TWO_DIGIT = setOf("20", "27", "30", "31", "32", "33", "34", "36", "39", "40", "41", "43", "44", "45", "46", "47",
        "48", "49", "51", "52", "53", "54", "55", "56", "57", "58", "60", "61", "62", "63", "64", "65", "66", "81", "82",
        "84", "86", "90", "91", "92", "93", "94", "95", "98")

    /** For logs: never the number, at most its country code. */
    fun redacted(raw: String?): String {
        val c = cleaned(raw) ?: return "none"
        return when { c.isEmpty() -> "none"; c.startsWith("+") -> (countryCode(c) ?: "+?") + "…"; else -> "local…" }
    }
}

/** One rule of GET /config/number-rules (16.3). First matching rule wins. */
data class NumberRule(
    val id: String, val type: String, val category: String, val action: String,
    val labelEn: String, val labelHi: String,
    val prefix: String? = null, val length: Int? = null, val countryCode: String? = null, val notLength: Int? = null,
    val minDigits: Int? = null, val maxDigits: Int? = null,
)

/** The phone's copy of NumberRuleService.classify (same semantics, same order). */
class NumberRules(val version: String, val rules: List<NumberRule>, riskyCodes: Collection<String>) {
    val risky: List<String> = riskyCodes.map { it.trim() }.filter { Regex("\\+?\\d{1,4}").matches(it) }
        .map { if (it.startsWith("+")) it else "+$it" }.distinct().sorted()

    fun classify(raw: String?): NumberRule? {
        val s = CallNumbers.cleaned(raw) ?: return null
        val national = CallNumbers.indianNational(CallNumbers.canonical(s))
        for (r in rules) {
            val hit = when (r.type) {
                "NATIONAL_PREFIX" -> national != null && national.length == r.length && national.startsWith(r.prefix ?: "\u0000")
                "COUNTRY_LENGTH" -> s.startsWith("+" + r.countryCode) && s.length - 1 - (r.countryCode?.length ?: 0) != r.notLength
                "E164_LENGTH" -> s.startsWith("+") && (s.length - 1 < (r.minDigits ?: 0) || s.length - 1 > (r.maxDigits ?: 99))
                "COUNTRY_CODE_IN" -> s.startsWith("+") && risky.any { s.startsWith(it) }
                "NOT_COUNTRY" -> s.startsWith("+") && !s.startsWith("+" + r.countryCode)
                else -> false
            }
            if (hit) return r
        }
        return null
    }

    /** Rule label with "{code}" filled in. */
    fun label(r: NumberRule, raw: String?, hindi: Boolean): String {
        val s = CallNumbers.cleaned(raw) ?: ""
        val code = (if (r.type == "COUNTRY_CODE_IN") risky.firstOrNull { s.startsWith(it) } else null) ?: CallNumbers.countryCode(s) ?: ""
        return (if (hindi) r.labelHi else r.labelEn).replace("{code}", code)
    }

    companion object {
        /** Built-in copy of backend seed/number-rules.json (version 2026-11-24.1): works before the first sync, offline. */
        val DEFAULT = NumberRules("2026-11-24.1", listOf(
            NumberRule("IN_1600_SERVICE", "NATIONAL_PREFIX", "BFSI_GOVT_SERVICE", "NEVER_BLOCK",
                "Official service call (1600 number) from a bank, insurer, investment firm or government office",
                "बैंक, बीमा, निवेश कंपनी या सरकारी विभाग की आधिकारिक सेवा कॉल (1600 नंबर)", prefix = "1600", length = 10),
            NumberRule("IN_140_PROMOTIONAL", "NATIONAL_PREFIX", "PROMOTIONAL", "NEVER_BLOCK",
                "Registered promotional call (140 number). To stop these, use DND: call or SMS 1909.",
                "पंजीकृत प्रचार कॉल (140 नंबर)। इन्हें रोकने के लिए DND लें: 1909 पर कॉल या SMS करें।", prefix = "140", length = 10),
            NumberRule("IN_ODD_LENGTH", "COUNTRY_LENGTH", "ODD_LENGTH", "WARN",
                "Unusual number: an Indian number has 10 digits after +91",
                "असामान्य नंबर: भारतीय नंबर में +91 के बाद 10 अंक होते हैं", countryCode = "91", notLength = 10),
            NumberRule("E164_ODD_LENGTH", "E164_LENGTH", "ODD_LENGTH", "WARN",
                "Unusual number length - be careful", "असामान्य लंबाई का नंबर - सावधान रहें", minDigits = 8, maxDigits = 15),
            NumberRule("RISKY_COUNTRY", "COUNTRY_CODE_IN", "RISKY_FOREIGN", "WARN",
                "Call from abroad ({code}) - government offices, banks and police never call you from a foreign number",
                "विदेश से कॉल ({code}) - सरकारी विभाग, बैंक और पुलिस कभी विदेशी नंबर से कॉल नहीं करते"),
            NumberRule("INTERNATIONAL", "NOT_COUNTRY", "INTERNATIONAL", "LABEL",
                "International call ({code})", "अंतरराष्ट्रीय कॉल ({code})", countryCode = "91"),
        ), listOf("+92"))
    }
}

/**
 * 16.4: the signed scam-number list (hash -> category), applied as deltas from GET /config/numbers?since=.
 * Text signed by the server (NumberReputationService.canonical):
 *   "fraudshield-numbers-v1\n" + version + "\n" + full + "\n" + "hash:category" (sorted by hash, ",") + "\n" + removed (sorted, ",")
 * A delta whose signature does not verify with [DomainGuard.BLOCKLIST_PUBLIC_KEY] is ignored as a whole.
 */
class ScamNumberList {
    @Volatile var version = 0L; private set
    private val map = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun size() = map.size
    fun categoryOf(hash: String): String? = map[hash]
    fun snapshot(): Map<String, String> = HashMap(map)

    fun restore(v: Long, entries: Map<String, String>) { map.clear(); map.putAll(entries); version = v }

    fun applyDelta(v: Long, full: Boolean, added: List<Pair<String, String>>, removed: List<String>, signature: String,
                   publicKey: String = DomainGuard.BLOCKLIST_PUBLIC_KEY): Boolean {
        if (!DomainGuard.verify(canonical(v, full, added, removed), signature, publicKey)) return false
        if (full) map.clear()
        for ((h, c) in added) if (h.length == 64) map[h] = c
        for (h in removed) map.remove(h)
        version = v
        return true
    }

    companion object {
        fun canonical(v: Long, full: Boolean, added: List<Pair<String, String>>, removed: List<String>) =
            "fraudshield-numbers-v1\n$v\n$full\n" + added.sortedBy { it.first }.joinToString(",") { "${it.first}:${it.second}" } +
                "\n" + removed.sorted().joinToString(",")
    }
}
