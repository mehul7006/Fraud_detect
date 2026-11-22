package com.fraudshield.app

/**
 * 6.6.1 rule: "app opened itself without the user". Pure Kotlin over a simplified event list so it can be unit-tested.
 *
 * Events used (UsageEvents, Android 10+): ACTIVITY_RESUMED (1), USER_INTERACTION (7), SCREEN_INTERACTIVE (15),
 * SCREEN_NON_INTERACTIVE (16). A resume of package P counts as a SELF-LAUNCH when P was not already in front and
 *   (a) the screen was off / non-interactive (an app lighting up the phone by itself), or
 *   (b) P replaced another normal app (not the launcher, System UI or P itself) and no USER_INTERACTION event happened
 *       in the 5 s before (a tap in the previous app that opens P - a link, a share - is user action).
 * Opening an app from the launcher, recents or the notification shade passes through launcher / System UI and is
 * never counted.
 *
 * Known limits (heuristic, documented): USER_INTERACTION is reported by Android in a rate-limited way, so a quick
 * hand-off between two apps can be miscounted once; the score therefore gives a small weight for 1-2 self-launches
 * and a large one only from 3 in 24 h. Only counts leave the phone, never the event list.
 */
object SelfLaunchRule {
    enum class Kind { RESUMED, USER_INTERACTION, SCREEN_ON, SCREEN_OFF }
    data class Ev(val time: Long, val kind: Kind, val pkg: String)

    const val TOUCH_WINDOW_MS = 5_000L

    fun count(events: List<Ev>, neutral: Set<String>): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        var interactive = true
        var lastFg: String? = null
        var lastTouch = Long.MIN_VALUE / 2
        for (e in events.sortedBy { it.time }) {
            when (e.kind) {
                Kind.SCREEN_OFF -> interactive = false
                Kind.SCREEN_ON -> interactive = true
                Kind.USER_INTERACTION -> lastTouch = e.time
                Kind.RESUMED -> {
                    if (e.pkg == lastFg) continue
                    val fromApp = lastFg != null && lastFg !in neutral
                    val noTouch = e.time - lastTouch > TOUCH_WINDOW_MS
                    if (e.pkg !in neutral && (!interactive || (fromApp && noTouch))) {
                        out[e.pkg] = (out[e.pkg] ?: 0) + 1
                    }
                    lastFg = e.pkg
                }
            }
        }
        return out
    }
}
