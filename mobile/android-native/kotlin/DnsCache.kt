package com.fraudshield.app

/**
 * 12.3: answers by (name, type), kept for their DNS TTL (at least 10 s, at most 1 h) - a repeated lookup is served
 * from memory without a network round trip. LRU, max [capacity] entries. Pure Kotlin.
 */
class DnsCache(private val capacity: Int = 2000, private val now: () -> Long = System::currentTimeMillis) {
    private class Entry(val answer: ByteArray, val expires: Long)
    private val map = object : LinkedHashMap<String, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Entry>) = size > capacity
    }
    @Volatile var hits = 0L; private set
    @Volatile var misses = 0L; private set

    @Synchronized
    fun get(name: String, qtype: Int, id: Int): ByteArray? {
        val e = map["$name|$qtype"]
        if (e == null || e.expires < now()) { misses++; if (e != null) map.remove("$name|$qtype"); return null }
        hits++
        return DnsPacket.withId(e.answer, id)
    }

    /** [ttlCap]: short TTL for names whose server check is still pending, so they are asked again soon. */
    @Synchronized
    fun put(name: String, qtype: Int, answer: ByteArray, ttlCap: Int = 3600) {
        val rcode = if (answer.size > 3) answer[3].toInt() and 0x0F else 2
        if (rcode != 0 && rcode != 3) return                      // never cache SERVFAIL / REFUSED
        val ttl = (DnsPacket.minTtl(answer) ?: 60).coerceIn(10, ttlCap.coerceAtLeast(10))
        map["$name|$qtype"] = Entry(answer, now() + ttl * 1000L)
    }

    @Synchronized fun clear() = map.clear()
}
