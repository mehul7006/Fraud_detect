package com.fraudshield.app

import java.nio.ByteBuffer

/**
 * 12.2: the few packet shapes the DNS-only VPN handles - IPv4 / IPv6 carrying a UDP DNS query to our virtual DNS
 * address - and the answers we send back. Pure Kotlin (unit-tested with captured packets).
 */
object DnsPacket {
    /** A DNS question sitting in an IP/UDP packet. [payload] = the DNS message (starts with the 2-byte id). */
    class Query(
        val ipVersion: Int, val src: ByteArray, val dst: ByteArray, val srcPort: Int, val dstPort: Int,
        val payload: ByteArray, val id: Int, val name: String, val qtype: Int,
    )

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val TYPE_HTTPS = 65

    /** Null when the packet is not a well-formed UDP DNS query. */
    fun parse(packet: ByteArray, length: Int = packet.size): Query? {
        if (length < 1) return null
        val version = (packet[0].toInt() shr 4) and 0xF
        val src: ByteArray
        val dst: ByteArray
        val udp: Int
        when (version) {
            4 -> {
                if (length < 28) return null
                val ihl = (packet[0].toInt() and 0xF) * 4
                if (packet[9].toInt() != 17 || ihl < 20 || length < ihl + 8) return null
                val frag = ((packet[6].toInt() and 0x1F) shl 8) or (packet[7].toInt() and 0xFF)
                if (frag != 0 || (packet[6].toInt() and 0x20) != 0) return null // fragments: not for DNS
                src = packet.copyOfRange(12, 16); dst = packet.copyOfRange(16, 20); udp = ihl
            }
            6 -> {
                if (length < 48 || packet[6].toInt() != 17) return null
                src = packet.copyOfRange(8, 24); dst = packet.copyOfRange(24, 40); udp = 40
            }
            else -> return null
        }
        val srcPort = u16(packet, udp)
        val dstPort = u16(packet, udp + 2)
        val udpLen = u16(packet, udp + 4)
        if (udpLen < 8 + 12 || udp + udpLen > length) return null
        val payload = packet.copyOfRange(udp + 8, udp + udpLen)
        if ((payload[2].toInt() and 0x80) != 0) return null // QR=1: an answer, not a question
        if (u16(payload, 4) < 1) return null
        val (name, end) = readName(payload, 12) ?: return null
        if (end + 4 > payload.size) return null
        return Query(version, src, dst, srcPort, dstPort, payload, u16(payload, 0), name, u16(payload, end))
    }

    /** The DNS answer [dns] wrapped back into an IP/UDP packet for [q]'s sender. */
    fun reply(q: Query, dns: ByteArray): ByteArray {
        val udpLen = 8 + dns.size
        return if (q.ipVersion == 4) {
            val b = ByteBuffer.allocate(20 + udpLen)
            b.put(0x45.toByte()).put(0).putShort((20 + udpLen).toShort()).putShort(0).putShort(0x4000.toShort())
                .put(64).put(17).putShort(0).put(q.dst).put(q.src)
            b.putShort(q.dstPort.toShort()).putShort(q.srcPort.toShort()).putShort(udpLen.toShort()).putShort(0).put(dns)
            val a = b.array()
            val sum = checksum(a, 0, 20, 0)
            a[10] = (sum shr 8).toByte(); a[11] = sum.toByte()
            a
        } else {
            val b = ByteBuffer.allocate(40 + udpLen)
            b.putInt(0x60000000).putShort(udpLen.toShort()).put(17).put(64).put(q.dst).put(q.src)
            b.putShort(q.dstPort.toShort()).putShort(q.srcPort.toShort()).putShort(udpLen.toShort()).putShort(0).put(dns)
            val a = b.array()
            // IPv6 UDP checksum is mandatory: pseudo header = src + dst + length + next header
            var pseudo = 0L
            for (i in 8 until 40 step 2) pseudo += u16(a, i)
            pseudo += udpLen + 17
            var sum = checksum(a, 40, udpLen, pseudo)
            if (sum == 0) sum = 0xFFFF
            a[46] = (sum shr 8).toByte(); a[47] = sum.toByte()
            a
        }
    }

    /** "This name does not exist" for a blocked domain: id and question copied, RCODE 3. */
    fun nxdomain(q: Query): ByteArray {
        val qEnd = (readName(q.payload, 12)?.second ?: 12) + 4
        val r = q.payload.copyOfRange(0, minOf(qEnd, q.payload.size))
        r[2] = (0x80 or (q.payload[2].toInt() and 0x79)).toByte() // QR=1, keep opcode + RD
        r[3] = 0x83.toByte()                                      // RA=1, RCODE=3 NXDOMAIN
        r[4] = 0; r[5] = 1; for (i in 6 until 12) r[i] = 0         // 1 question, no answers
        return r
    }

    /** Smallest TTL in the answer section (seconds), or null when there is none (e.g. NXDOMAIN). */
    fun minTtl(dns: ByteArray): Int? {
        if (dns.size < 12) return null
        val qd = u16(dns, 4); val an = u16(dns, 6)
        var p = 12
        repeat(qd) { p = (readName(dns, p)?.second ?: return null) + 4 }
        var min: Int? = null
        repeat(an) {
            p = skipName(dns, p) ?: return min
            if (p + 10 > dns.size) return min
            val ttl = ((dns[p + 4].toLong() and 0xFF shl 24) or (dns[p + 5].toLong() and 0xFF shl 16) or
                    (dns[p + 6].toLong() and 0xFF shl 8) or (dns[p + 7].toLong() and 0xFF)).toInt()
            min = if (min == null) ttl else minOf(min!!, ttl)
            p += 10 + u16(dns, p + 8)
        }
        return min
    }

    fun withId(dns: ByteArray, id: Int): ByteArray = dns.copyOf().also { it[0] = (id shr 8).toByte(); it[1] = id.toByte() }

    /** Reads a (possibly compressed) name; returns it lower case without trailing dot + the offset after it. */
    fun readName(b: ByteArray, start: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var p = start
        var end = -1
        var jumps = 0
        while (true) {
            if (p >= b.size) return null
            val len = b[p].toInt() and 0xFF
            when {
                len == 0 -> { if (end < 0) end = p + 1; break }
                len and 0xC0 == 0xC0 -> {
                    if (p + 1 >= b.size || ++jumps > 20) return null
                    if (end < 0) end = p + 2
                    p = ((len and 0x3F) shl 8) or (b[p + 1].toInt() and 0xFF)
                }
                len > 63 -> return null
                else -> {
                    if (p + 1 + len > b.size) return null
                    if (sb.isNotEmpty()) sb.append('.')
                    for (i in 1..len) sb.append((b[p + i].toInt() and 0xFF).toChar())
                    p += 1 + len
                    if (sb.length > 253) return null
                }
            }
        }
        return sb.toString().lowercase() to end
    }

    private fun skipName(b: ByteArray, start: Int): Int? = readName(b, start)?.second

    fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun checksum(b: ByteArray, off: Int, len: Int, initial: Long): Int {
        var sum = initial
        var i = off
        while (i + 1 < off + len) { sum += u16(b, i); i += 2 }
        if (i < off + len) sum += (b[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    /** Builds a DNS query message (used by tests and by the DoH probe). */
    fun query(id: Int, name: String, qtype: Int): ByteArray {
        val b = ByteBuffer.allocate(12 + name.length + 2 + 4)
        b.putShort(id.toShort()).putShort(0x0100).putShort(1).putShort(0).putShort(0).putShort(0)
        for (label in name.trimEnd('.').split('.')) { b.put(label.length.toByte()); b.put(label.toByteArray(Charsets.US_ASCII)) }
        b.put(0).putShort(qtype.toShort()).putShort(1)
        return b.array().copyOf(b.position())
    }

    /** Wraps a DNS message into a UDP/IPv4 or IPv6 packet (tests: build "captured" packets). */
    fun wrap(ipVersion: Int, src: ByteArray, dst: ByteArray, srcPort: Int, dstPort: Int, dns: ByteArray): ByteArray {
        val fake = Query(ipVersion, dst, src, dstPort, srcPort, dns, 0, "", 0) // reply() swaps: so swap here
        return reply(fake, dns)
    }
}
