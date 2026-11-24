package com.fraudshield.app

/**
 * 16.7: the local call decision. Fully offline, pure Kotlin (JVM-tested), a few microseconds per call - far inside
 * Android's 5 s CallScreeningService limit.
 *
 * Order (first that applies wins):
 *  1. hidden / private number            -> mode 4 rejects, otherwise allowed with a "hidden number" label
 *  2. short / service codes (112, 1930, 1800 toll-free, bank short codes) -> ALWAYS allowed, never touched
 *  3. TRAI protected series (1600 BFSI / government, 140 promotional)       -> ALWAYS allowed (TRAI: may not be blocked)
 *  4. always-allow: contact, starred, recently called by the user, user allow list -> allowed, no notification
 *  5. user's own block list ("Block number")                               -> rejected in every mode
 *  6. signed scam list (crowd reports, 16.4)    -> mode 1 warns only, modes 2-4 reject
 *  7. pattern rules WARN (odd length, risky country) -> mode 3 silences (rings silently, still listed), mode 4 rejects,
 *     modes 1-2 warn
 *  8. everything else unknown (incl. international LABEL) -> mode 4 rejects, otherwise allowed with a label
 */
object CallGuardEngine {
    enum class Mode(val n: Int) {
        LABEL_ONLY(1), BLOCK_SCAM(2), BLOCK_SCAM_SILENCE_RISKY(3), REJECT_UNKNOWN(4);
        companion object { fun of(n: Int) = values().firstOrNull { it.n == n } ?: BLOCK_SCAM }
    }

    enum class Action { ALLOW, SILENCE, REJECT }

    enum class Kind { HIDDEN, SERVICE_CODE, PROTECTED_SERIES, KNOWN, USER_BLOCKED, SCAM_LIST, RULE_WARN, RULE_LABEL, UNKNOWN }

    /** What the phone knows about this caller - all looked up locally. */
    data class Input(
        val raw: String?,
        val contact: Boolean = false,
        val starred: Boolean = false,
        val recentOutgoing: Boolean = false,
    )

    data class Lists(
        val mode: Mode,
        val allow: Set<String> = emptySet(),   // canonical numbers
        val block: Set<String> = emptySet(),   // canonical numbers
        val scam: ScamNumberList = ScamNumberList(),
        val rules: NumberRules = NumberRules.DEFAULT,
    )

    data class Decision(
        val action: Action, val kind: Kind, val canonical: String?,
        val rule: NumberRule? = null, val scamCategory: String? = null,
        /** show a notification for this call (16.9); known callers and service codes are left alone */
        val notify: Boolean = true,
    ) {
        val rejected get() = action == Action.REJECT
    }

    fun decide(input: Input, l: Lists): Decision {
        val cleaned = CallNumbers.cleaned(input.raw)
        // 1. hidden number
        if (cleaned.isNullOrEmpty() || cleaned.none { it.isDigit() })
            return Decision(if (l.mode == Mode.REJECT_UNKNOWN) Action.REJECT else Action.ALLOW, Kind.HIDDEN, null)
        // 2. emergency / short / toll-free service codes
        if (CallNumbers.isShortOrService(cleaned)) return Decision(Action.ALLOW, Kind.SERVICE_CODE, null, notify = false)
        val canonical = CallNumbers.canonical(cleaned)
        val rule = l.rules.classify(cleaned)
        // 3. TRAI: 1600 / 140 series may not be blocked or tagged as spam
        if (rule != null && rule.action == "NEVER_BLOCK") return Decision(Action.ALLOW, Kind.PROTECTED_SERIES, canonical, rule)
        // 4. people the user knows
        if (input.contact || input.starred || input.recentOutgoing || (canonical != null && canonical in l.allow))
            return Decision(Action.ALLOW, Kind.KNOWN, canonical, rule, notify = false)
        // 5. the user's own block list
        if (canonical != null && canonical in l.block) return Decision(Action.REJECT, Kind.USER_BLOCKED, canonical, rule)
        // 6. crowd-reported scam numbers
        val scam = canonical?.let { l.scam.categoryOf(CallNumbers.hash(it)) }
        if (scam != null)
            return Decision(if (l.mode == Mode.LABEL_ONLY) Action.ALLOW else Action.REJECT, Kind.SCAM_LIST, canonical, rule, scam)
        // 7. pattern warnings
        if (rule != null && rule.action == "WARN") {
            val a = when (l.mode) {
                Mode.REJECT_UNKNOWN -> Action.REJECT
                Mode.BLOCK_SCAM_SILENCE_RISKY -> Action.SILENCE
                else -> Action.ALLOW
            }
            return Decision(a, Kind.RULE_WARN, canonical, rule)
        }
        // 8. unknown caller
        val a = if (l.mode == Mode.REJECT_UNKNOWN) Action.REJECT else Action.ALLOW
        return Decision(a, if (rule != null) Kind.RULE_LABEL else Kind.UNKNOWN, canonical, rule)
    }
}

/**
 * 16.11: what the notification / log calls the caller. Order: saved contact name, else what FraudShield knows
 * (scam reports, rule label), else the name the phone network / system provided, else "Unknown number".
 * Never invents a name.
 */
object CallerLabeler {
    fun label(d: CallGuardEngine.Decision, contactName: String?, systemName: String?, rules: NumberRules, raw: String?,
              hindi: Boolean): String {
        if (!contactName.isNullOrBlank()) return contactName
        val s = when (d.kind) {
            CallGuardEngine.Kind.HIDDEN -> if (hindi) "छिपा हुआ नंबर" else "Hidden number"
            CallGuardEngine.Kind.SCAM_LIST -> scamLabel(d.scamCategory, hindi)
            CallGuardEngine.Kind.USER_BLOCKED -> if (hindi) "आपके द्वारा ब्लॉक किया गया नंबर" else "Number you blocked"
            CallGuardEngine.Kind.PROTECTED_SERIES, CallGuardEngine.Kind.RULE_WARN, CallGuardEngine.Kind.RULE_LABEL ->
                d.rule?.let { rules.label(it, raw, hindi) }
            else -> null
        }
        if (s != null) return s
        if (!systemName.isNullOrBlank()) return systemName
        return if (hindi) "अज्ञात नंबर" else "Unknown number"
    }

    fun scamLabel(category: String?, hindi: Boolean) = when (category) {
        "TELEMARKETING" -> if (hindi) "कई लोगों ने टेलीमार्केटिंग के रूप में रिपोर्ट किया" else "Reported as telemarketing by many people"
        "SPAM" -> if (hindi) "कई लोगों ने स्पैम के रूप में रिपोर्ट किया" else "Reported as spam by many people"
        else -> if (hindi) "कई लोगों ने धोखाधड़ी के रूप में रिपोर्ट किया" else "Reported as fraud by many people"
    }

    /** Short outcome line for the notification. */
    fun outcome(a: CallGuardEngine.Action, hindi: Boolean) = when (a) {
        CallGuardEngine.Action.REJECT -> if (hindi) "कॉल काट दी गई" else "Call rejected"
        CallGuardEngine.Action.SILENCE -> if (hindi) "कॉल बिना आवाज़ के आई" else "Call silenced"
        CallGuardEngine.Action.ALLOW -> if (hindi) "कॉल आने दी गई" else "Call allowed"
    }
}
