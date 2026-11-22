package com.fraudshield.app

/**
 * Per-package notification counting (6.4). Pure functions over the stored string "t1,t2,..." so they can be tested
 * with a fake clock. Only timestamps are kept - never notification content.
 */
object NotifCounter {
    const val HOUR = 3_600_000L
    const val MAX_PER_PACKAGE = 100 // a spammer at 100/h is already far past every threshold

    private fun parse(stored: String?): List<Long> =
        (stored ?: "").split(",").mapNotNull { it.toLongOrNull() }

    /** Adds [now], drops entries older than one hour and keeps at most the newest [MAX_PER_PACKAGE]. */
    fun add(stored: String?, now: Long): String =
        (parse(stored).filter { now - it < HOUR } + now).takeLast(MAX_PER_PACKAGE).joinToString(",")

    fun count(stored: String?, now: Long): Int = parse(stored).count { now - it < HOUR }

    /** Keys ("n_<package>") whose package is no longer installed, to be removed. */
    fun staleKeys(keys: Collection<String>, installed: Set<String>): List<String> =
        keys.filter { it.startsWith("n_") && it.removePrefix("n_") !in installed }
}
