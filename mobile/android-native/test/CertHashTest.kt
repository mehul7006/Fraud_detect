package com.fraudshield.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CertHashTest {
    @Test fun knownSha256Vector() {
        // SHA-256("abc"), FIPS 180-2 test vector
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", CertHash.sha256Hex("abc".toByteArray()))
    }

    @Test fun severalCertsAreCommaSeparatedAndDeduplicated() {
        val a = "abc".toByteArray()
        val b = "abd".toByteArray()
        val s = CertHash.join(listOf(a, b, a))!!
        assertEquals(2, s.split(",").size)
        assertTrue(s.split(",").all { it.matches(Regex("[0-9a-f]{64}")) })
    }

    @Test fun capFitsTheServerLimit() {
        val many = (1..20).map { "cert$it".toByteArray() }
        assertTrue(CertHash.join(many)!!.length <= 400)
    }

    @Test fun noCertsIsNull() { assertNull(CertHash.join(emptyList())) }
}
