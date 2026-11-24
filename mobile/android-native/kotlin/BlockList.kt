package com.fraudshield.app

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * 18.3: the phone-wide ad / tracker / malware list in compact form. Pure Kotlin.
 * File format FSBL1 (built by ml/blocklists/build_blocklists.py, served by BlocklistFileService):
 *   "FSBL1" | version u64 BE | count u32 BE | count x (hash i64 BE, category u8) sorted by signed hash.
 * hash = first 8 bytes of SHA-256(lower-case domain). Kept as a sorted LongArray + ByteArray: 9 bytes per domain
 * (542k domains = 4.9 MB), binary search per parent name, no domain strings in memory or on disk.
 */
class BlockList private constructor(val version: Long, private val hashes: LongArray, private val cats: ByteArray) {
    val size get() = hashes.size

    /** Category of [name] or of its nearest listed parent (0 = not listed). */
    fun category(name: String): Int {
        val n = name.lowercase().trimEnd('.')
        if (hashes.isEmpty() || !n.contains('.')) return 0
        for (p in DomainGuard.parents(n)) {
            val i = hashes.binarySearch(hash64(p))
            if (i >= 0) return cats[i].toInt()
        }
        return 0
    }

    fun counts(): Map<String, Int> {
        val c = IntArray(4)
        for (b in cats) c[b.toInt()]++
        return mapOf("ads" to c[ADS], "trackers" to c[TRACKERS], "malware" to c[MALWARE])
    }

    companion object {
        const val ADS = 1
        const val TRACKERS = 2
        const val MALWARE = 3
        val NAMES = mapOf(ADS to "ads", TRACKERS to "trackers", MALWARE to "malware")
        val EMPTY = BlockList(0, LongArray(0), ByteArray(0))

        private val sha = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

        fun hash64(domain: String): Long {
            val d = sha.get().digest(domain.toByteArray(Charsets.UTF_8))
            var h = 0L
            for (i in 0 until 8) h = (h shl 8) or (d[i].toLong() and 0xFF)
            return h
        }

        /** Parses (and fully checks) an FSBL1 stream: magic, sorted unique hashes, known categories. */
        fun read(input: InputStream): BlockList {
            val d = DataInputStream(BufferedInputStream(input, 1 shl 16))
            val magic = ByteArray(5); d.readFully(magic)
            require(String(magic, Charsets.US_ASCII) == "FSBL1") { "not an FSBL1 block list" }
            val version = d.readLong()
            val n = d.readInt()
            require(n in 0..5_000_000) { "bad count $n" }
            val h = LongArray(n); val c = ByteArray(n)
            for (i in 0 until n) {
                h[i] = d.readLong(); c[i] = d.readByte()
                require(i == 0 || h[i] > h[i - 1]) { "hashes not sorted at $i" }
                require(c[i] in 1..3) { "unknown category ${c[i]}" }
            }
            require(d.read() == -1) { "trailing bytes" }
            return BlockList(version, h, c)
        }

        /** What the server signs (BlocklistFileService.canonical); the app also checks the file's SHA-256. */
        fun canonical(variant: String, version: Long, count: Int, sha256: String) =
            "fraudshield-blocklists-v1\n$variant\n$version\n$count\n$sha256"

        fun sha256Hex(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    }
}
