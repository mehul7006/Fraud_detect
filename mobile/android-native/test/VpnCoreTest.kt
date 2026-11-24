package com.fraudshield.app

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

/** 12.2 packet parser, 12.3 cache, 12.4 guard, 12.5 signature check - on built "captured" packets. */
class VpnCoreTest {
    private val phone4 = byteArrayOf(10, 111, 222.toByte(), 2)
    private val dns4 = byteArrayOf(10, 111, 222.toByte(), 1)
    private val phone6 = ByteArray(16).also { it[0] = 0xfd.toByte(); it[1] = 0xfd.toByte(); it[15] = 2 }
    private val dns6 = ByteArray(16).also { it[0] = 0xfd.toByte(); it[1] = 0xfd.toByte(); it[15] = 1 }

    @Test fun parses_ipv4_and_ipv6_dns_queries() {
        val q4 = DnsPacket.parse(DnsPacket.wrap(4, phone4, dns4, 40123, 53, DnsPacket.query(0x1234, "WWW.Fleepkart.com", DnsPacket.TYPE_A)))!!
        assertEquals("www.fleepkart.com", q4.name)
        assertEquals(DnsPacket.TYPE_A, q4.qtype)
        assertEquals(0x1234, q4.id)
        assertEquals(40123, q4.srcPort)
        assertEquals(53, q4.dstPort)
        val q6 = DnsPacket.parse(DnsPacket.wrap(6, phone6, dns6, 5353, 53, DnsPacket.query(7, "sbi.co.in", DnsPacket.TYPE_AAAA)))!!
        assertEquals(6, q6.ipVersion)
        assertEquals("sbi.co.in", q6.name)
        assertEquals(DnsPacket.TYPE_AAAA, q6.qtype)
    }

    @Test fun ignores_non_dns_and_broken_packets() {
        assertNull(DnsPacket.parse(ByteArray(10)))
        val tcp = DnsPacket.wrap(4, phone4, dns4, 1, 53, DnsPacket.query(1, "a.com", 1)).also { it[9] = 6 } // TCP
        assertNull(DnsPacket.parse(tcp))
        val answer = DnsPacket.query(1, "a.com", 1).also { it[2] = 0x81.toByte() } // QR=1
        assertNull(DnsPacket.parse(DnsPacket.wrap(4, phone4, dns4, 1, 53, answer)))
        val truncated = DnsPacket.wrap(4, phone4, dns4, 1, 53, DnsPacket.query(1, "abc.com", 1))
        assertNull(DnsPacket.parse(truncated, truncated.size - 6))
    }

    @Test fun reply_swaps_addresses_and_has_valid_checksums() {
        val q = DnsPacket.parse(DnsPacket.wrap(4, phone4, dns4, 40000, 53, DnsPacket.query(9, "x.test", 1)))!!
        val r = DnsPacket.reply(q, DnsPacket.nxdomain(q))
        assertArrayEquals(dns4, r.copyOfRange(12, 16))
        assertArrayEquals(phone4, r.copyOfRange(16, 20))
        assertEquals(53, DnsPacket.u16(r, 20)); assertEquals(40000, DnsPacket.u16(r, 22))
        assertEquals(0, onesSum(r, 0, 20, 0), )                      // IPv4 header checksum verifies
        val q6 = DnsPacket.parse(DnsPacket.wrap(6, phone6, dns6, 41000, 53, DnsPacket.query(9, "x.test", 1)))!!
        val r6 = DnsPacket.reply(q6, DnsPacket.nxdomain(q6))
        var pseudo = 0L
        for (i in 8 until 40 step 2) pseudo += DnsPacket.u16(r6, i)
        pseudo += (r6.size - 40) + 17
        assertEquals(0, onesSum(r6, 40, r6.size - 40, pseudo))     // IPv6 UDP checksum verifies
    }

    @Test fun nxdomain_and_ttl() {
        val q = DnsPacket.parse(DnsPacket.wrap(4, phone4, dns4, 1, 53, DnsPacket.query(0x4242, "evil.xyz", 1)))!!
        val nx = DnsPacket.nxdomain(q)
        assertEquals(0x4242, DnsPacket.u16(nx, 0))
        assertEquals(3, nx[3].toInt() and 0x0F)
        assertEquals(1, DnsPacket.u16(nx, 4)); assertEquals(0, DnsPacket.u16(nx, 6))
        assertNull(DnsPacket.minTtl(nx))
        assertEquals(300, DnsPacket.minTtl(answerA("evil.xyz", 300)))
    }

    @Test fun cache_honours_ttl_and_rewrites_the_id() {
        var t = 1_000_000L
        val c = DnsCache(now = { t })
        c.put("a.com", 1, answerA("a.com", 60))
        val hit = c.get("a.com", 1, 0x7777)!!
        assertEquals(0x7777, DnsPacket.u16(hit, 0))
        t += 61_000
        assertNull(c.get("a.com", 1, 1))
        c.put("b.com", 1, answerA("b.com", 3600), ttlCap = 30)
        t += 31_000
        assertNull(c.get("b.com", 1, 1), "pending names expire quickly")
        val servfail = answerA("c.com", 60).also { it[3] = 0x82.toByte() }
        c.put("c.com", 1, servfail)
        assertNull(c.get("c.com", 1, 1), "SERVFAIL is never cached")
    }

    @Test fun guard_blocks_listed_domains_and_their_subdomains_and_doh() {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val pub = Base64.getEncoder().encodeToString(kp.public.encoded)
        fun sign(text: String) = Base64.getEncoder().encodeToString(Signature.getInstance("SHA256withECDSA").run {
            initSign(kp.private); update(text.toByteArray()); sign() })
        val g = DomainGuard()
        val added = listOf(DomainGuard.sha256("fake-bank.xyz"))
        val sig = sign(DomainGuard.canonical(5, true, added, emptyList()))
        assertFalse("tampered list rejected", g.applyDelta(5, true, added + DomainGuard.sha256("google.com"), emptyList(), sig, pub))
        assertEquals(0, g.blocklistSize())
        assertTrue(g.applyDelta(5, true, added, emptyList(), sig, pub))
        assertEquals(DomainGuard.Action.BLOCK_LIST, g.decide("fake-bank.xyz"))
        assertEquals(DomainGuard.Action.BLOCK_LIST, g.decide("login.secure.FAKE-bank.xyz."))
        assertEquals(DomainGuard.Action.CHECK, g.decide("bank.xyz"))
        // delta: remove + add
        val add2 = listOf(DomainGuard.sha256("other-fake.top"))
        assertTrue(g.applyDelta(6, false, add2, added, sign(DomainGuard.canonical(6, false, add2, added)), pub))
        assertEquals(DomainGuard.Action.CHECK, g.decide("fake-bank.xyz"))
        assertEquals(DomainGuard.Action.BLOCK_LIST, g.decide("other-fake.top"))
        assertEquals(6, g.version)
        assertFalse("wrong key", g.applyDelta(7, true, emptyList(), emptyList(), sig, DomainGuard.BLOCKLIST_PUBLIC_KEY))
        // DoH, local names, verdicts, local-only mode
        assertEquals(DomainGuard.Action.BLOCK_DOH, g.decide("dns.google"))
        assertEquals(DomainGuard.Action.BLOCK_DOH, g.decide("mozilla.cloudflare-dns.com"))
        assertEquals(DomainGuard.Action.ALLOW, g.decide("printer.local"))
        assertEquals(DomainGuard.Action.ALLOW, g.decide("1.0.0.10.in-addr.arpa"))
        g.remember("fleepkart.com", DomainGuard.Verdict(true, "Flipkart", "https://www.flipkart.com", System.currentTimeMillis()))
        assertEquals(DomainGuard.Action.BLOCK_VERDICT, g.decide("www.fleepkart.com"))
        assertEquals("Flipkart", g.verdictFor("www.fleepkart.com")!!.brand)
        g.remember("flipkart.com", DomainGuard.Verdict(false, null, null, System.currentTimeMillis()))
        assertEquals(DomainGuard.Action.ALLOW, g.decide("www.flipkart.com"))
        g.localOnly = true
        assertEquals(DomainGuard.Action.ALLOW, g.decide("unknown-shop.in"))
        assertEquals("local-only still uses the list", DomainGuard.Action.BLOCK_LIST, g.decide("other-fake.top"))
    }

    @Test fun shipped_public_key_is_a_valid_p256_key() {
        val k = java.security.KeyFactory.getInstance("EC")
            .generatePublic(java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(DomainGuard.BLOCKLIST_PUBLIC_KEY)))
        assertEquals("EC", k.algorithm)
    }

    private fun assertNull(v: Any?, msg: String) = assertNull(msg, v)

    /** A DNS answer with one A record. */
    private fun answerA(name: String, ttl: Int): ByteArray {
        val q = DnsPacket.query(1, name, 1)
        q[2] = 0x81.toByte(); q[3] = 0x80.toByte(); q[7] = 1
        val rr = byteArrayOf(0xC0.toByte(), 12, 0, 1, 0, 1, (ttl shr 24).toByte(), (ttl shr 16).toByte(), (ttl shr 8).toByte(),
            ttl.toByte(), 0, 4, 93, 184.toByte(), 215.toByte(), 14)
        return q + rr
    }

    private fun onesSum(b: ByteArray, off: Int, len: Int, initial: Long): Int {
        var sum = initial
        var i = off
        while (i + 1 < off + len) { sum += DnsPacket.u16(b, i); i += 2 }
        if (i < off + len) sum += (b[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }
}
