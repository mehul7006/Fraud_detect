package com.fraudshield.app

/**
 * 15.2 / 15.4: pure decision logic of the live-scam guard (no Android types, unit-tested on the JVM).
 *
 * A "scam session" is ARMED only by an incoming call from a number that is not a saved contact. Nothing about
 * banking apps or one-time codes is ever recorded, sent or remembered unless such a call is going on or ended less
 * than [windowMs] ago - so ordinary banking never leaves a trace. The four signals mirror the server
 * (ScamSessionService): LONG_UNKNOWN_CALL, REMOTE_ACCESS_APP, BANKING_APP, OTP_SEEN. The phone decides on its own
 * (works offline, same rule as the server); the server copy only adds the threat record / guardian push.
 *
 * Only signal KINDS and the call length leave the phone - never a number, an app name or a code.
 */
class ScamSignalLogic(
    val windowMs: Long = 600_000L,      // server default fraudshield.scam-session.window-minutes = 10
    val longCallMs: Long = 180_000L,    // server default long-call-seconds = 180
) {
    enum class Kind { LONG_UNKNOWN_CALL, REMOTE_ACCESS_APP, BANKING_APP, OTP_SEEN }
    enum class Level { NONE, WARNING, CRITICAL }

    private var callStart: Long? = null       // a call is going on (any call)
    private var callUnknown = false           // ...and it is an incoming call from an unknown number
    private var armedUntil = 0L                // unknown call active, or ended less than windowMs ago
    private val seen = HashMap<Kind, Long>()   // kind -> last time seen (only while armed)
    private val sent = HashMap<Kind, Long>()   // kind -> last time sent to the server
    private var alerted = Level.NONE
    private var alertedAt = 0L

    val callActive get() = callStart != null

    fun armed(now: Long) = (callStart != null && callUnknown) || now < armedUntil

    /** Incoming call answered (OFFHOOK after RINGING) or an outgoing call started (unknown = false). */
    fun onCallStarted(now: Long, unknownIncoming: Boolean) {
        callStart = now
        callUnknown = unknownIncoming
        if (unknownIncoming) armedUntil = Long.MAX_VALUE
    }

    fun onCallEnded(now: Long) {
        tick(now) // a call that reached the long-call mark counts even if it ends between two polls
        if (callStart != null && callUnknown) armedUntil = now + windowMs
        callStart = null
        callUnknown = false
    }

    /** The long-call signal is raised by time passing; call this from the poll loop. */
    fun tick(now: Long) {
        val start = callStart ?: return
        if (callUnknown && now - start >= longCallMs) seen[Kind.LONG_UNKNOWN_CALL] = now
    }

    fun callSeconds(now: Long): Int = callStart?.let { ((now - it) / 1000).toInt() } ?: 0

    /** An app came to the foreground. Returns the signal it raised, if any (only while armed). */
    fun onForeground(now: Long, pkg: String, remoteAccess: Set<String>, banking: Set<String>): Kind? {
        if (!armed(now)) return null
        val k = when (pkg) {
            in remoteAccess -> Kind.REMOTE_ACCESS_APP
            in banking -> Kind.BANKING_APP
            else -> return null
        }
        seen[k] = now
        return k
    }

    /**
     * A one-time-code notification arrived (the code itself is never passed in here).
     * @return true when the OTP-share guard (15.4) must warn now: any call is going on.
     */
    fun onOtp(now: Long): Boolean {
        if (armed(now)) seen[Kind.OTP_SEEN] = now
        return callActive
    }

    fun seenKinds(now: Long): Set<Kind> = seen.filterValues { now - it <= windowMs }.keys

    /** Same rule as the server: all four within the window = CRITICAL; call + screen sharing + one more = WARNING. */
    fun level(now: Long): Level {
        val s = seenKinds(now)
        return when {
            s.size == Kind.values().size -> Level.CRITICAL
            s.size == 3 && Kind.LONG_UNKNOWN_CALL in s && Kind.REMOTE_ACCESS_APP in s -> Level.WARNING
            else -> Level.NONE
        }
    }

    /**
     * Signals to send to the server now. Nothing is sent before the call itself qualifies (unknown + long), so a short
     * call never reports anything; after that each kind is (re)sent at most once per half window, keeping the server's
     * 10-minute window filled while the scam goes on.
     */
    fun toSend(now: Long): List<Kind> {
        val s = seenKinds(now)
        if (Kind.LONG_UNKNOWN_CALL !in s) return emptyList()
        return s.filter { k -> sent[k]?.let { now - it >= windowMs / 2 } ?: true }.sortedBy { it.ordinal }
    }

    fun markSent(k: Kind, now: Long) { sent[k] = now }

    /** A new alert to show: only when the level rises (or the last alert is older than the window). */
    fun newAlert(now: Long): Level? {
        val l = level(now)
        if (l == Level.NONE) return null
        if (l.ordinal > alerted.ordinal || now - alertedAt > windowMs) {
            alerted = l; alertedAt = now
            return l
        }
        return null
    }

    /** Forget everything once nothing is armed any more (no history is kept). */
    fun expire(now: Long) {
        if (!armed(now) && seen.values.all { now - it > windowMs }) {
            seen.clear(); sent.clear(); alerted = Level.NONE
        }
    }

    companion object {
        private val OTP_WORDS = Regex(
            "(\\botp\\b|one[ -]?time[ -]?(pass(word|code)?|pin|code)|verification code|security code|login code|auth(entication)? code|" +
                "ओटीपी|वन टाइम|सत्यापन कोड)", RegexOption.IGNORE_CASE)
        private val CODE = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])")

        /** Looks like a one-time-code notification. The text is only inspected, never kept. */
        fun isOtpText(text: String): Boolean = OTP_WORDS.containsMatchIn(text) && CODE.containsMatchIn(text)

        /** Banking / UPI apps (package names to re-check on Google Play before release). */
        val BANKING = setOf(
            "com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user", "in.org.npci.upiapp",
            "com.sbi.lotusintouch", "com.snapwork.hdfc", "com.csam.icici.bank.imobile", "com.axis.mobile",
            "com.msf.kbank.mobile", "com.bankofbaroda.mconnect", "com.pnb.pnbone", "com.dreamplug.androidapp",
            "com.mobikwik_new", "com.freecharge.android")

        /** Fallback until /scam-signals/config was fetched once - same list as the server default. */
        val REMOTE_ACCESS_DEFAULT = setOf(
            "com.anydesk.anydeskandroid", "com.teamviewer.quicksupport.market", "com.teamviewer.teamviewer.market.mobile",
            "com.carriez.flutter_hbb", "com.sand.airdroid", "com.google.chromeremotedesktop", "com.microsoft.rdc.androidx",
            "com.realvnc.viewer.android")
    }
}
