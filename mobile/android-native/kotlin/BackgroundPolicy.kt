package com.fraudshield.app

/**
 * 7.11: what to do with a link / notice seen by the notification listener. Pure logic (unit tested).
 *  - app on screen       -> QUEUE: the Flutter app scans it and shows its in-app alert (unchanged behaviour)
 *  - app closed / away   -> SCAN now in the background (no need to open FraudShield)
 *  - no saved session    -> QUEUE: scanned the next time the app runs (never lost)
 *  - app turned off for that messenger, or a trusted site (4.9) -> SKIP
 */
object BackgroundPolicy {
    enum class Action { SCAN, QUEUE, SKIP }

    fun forLink(host: String?, pkg: String?, disabledPackages: Set<String>, trustedDomains: Collection<String>,
                appInForeground: Boolean, hasSession: Boolean): Action = when {
        pkg != null && pkg in disabledPackages -> Action.SKIP
        host != null && isTrusted(host, trustedDomains) -> Action.SKIP
        appInForeground || !hasSession -> Action.QUEUE
        else -> Action.SCAN
    }

    /** Host of a link found in a notification ("example.com/x" has no scheme - add one before parsing). */
    fun hostOf(url: String): String? = runCatching {
        java.net.URI(if (url.contains("://")) url else "http://$url").host?.lowercase()
    }.getOrNull()

    /** Same rule as the app (ProtectionEngine.isTrusted): the domain itself or a sub-domain of it. */
    fun isTrusted(host: String, trusted: Collection<String>): Boolean {
        val h = host.lowercase().trimEnd('.')
        return trusted.any { d -> val t = d.lowercase().trim().trimEnd('.'); t.isNotEmpty() && (h == t || h.endsWith(".$t")) }
    }

    /** Notification text for a background verdict; null = safe, stay quiet (same as the app). */
    fun alertFor(verdict: String, url: String, reasons: List<String>): Pair<String, String>? = when (verdict) {
        "MALICIOUS" -> "Dangerous link!" to "$url\n${reasons.joinToString(", ")}\nDo not open it."
        "SUSPICIOUS" -> "Suspicious link" to "$url\n${reasons.joinToString(", ")}"
        else -> null
    }

    /** Server error classes: retry later through the app's offline queue, or give up quietly. */
    fun queueOnFailure(httpStatus: Int): Boolean = httpStatus == 0 || httpStatus == 401 || httpStatus == 408 ||
        httpStatus == 429 || httpStatus >= 500
}
