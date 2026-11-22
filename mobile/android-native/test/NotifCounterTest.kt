package com.fraudshield.app

import org.junit.Assert.*
import org.junit.Test

/** Roadmap 6.4. */
class NotifCounterTest {
    private val t0 = 1_000_000_000L

    private fun addN(n: Int, start: Long, step: Long, stored: String? = null): String? {
        var s = stored
        repeat(n) { s = NotifCounter.add(s, start + it * step) }
        return s
    }

    @Test fun twentyFiveInOneHourGiveTwentyFive() {
        val s = addN(25, t0, 60_000) // one per minute
        assertEquals(25, NotifCounter.count(s, t0 + 25 * 60_000))
    }

    @Test fun eventsOlderThanOneHourDropOut() {
        val s = addN(10, t0, 1_000)
        assertEquals(10, NotifCounter.count(s, t0 + 30 * 60_000))
        assertEquals(0, NotifCounter.count(s, t0 + NotifCounter.HOUR + 20_000))
        // adding a new one also prunes the stored string
        assertEquals(1, NotifCounter.add(s, t0 + 2 * NotifCounter.HOUR).split(",").size)
    }

    @Test fun packagesAreIndependent() {
        val a = addN(30, t0, 1_000)
        val b = addN(2, t0, 1_000)
        assertEquals(30, NotifCounter.count(a, t0 + 60_000))
        assertEquals(2, NotifCounter.count(b, t0 + 60_000))
    }

    @Test fun storedTimestampsAreCappedPerPackage() {
        val s = addN(500, t0, 100)
        assertEquals(NotifCounter.MAX_PER_PACKAGE, s!!.split(",").size)
        assertEquals(NotifCounter.MAX_PER_PACKAGE, NotifCounter.count(s, t0 + 500 * 100))
    }

    @Test fun badStoredValueIsIgnored() {
        assertEquals(0, NotifCounter.count("x,,y", t0))
        assertEquals(0, NotifCounter.count(null, t0))
    }

    @Test fun staleKeysAreOnlyCountersOfUninstalledApps() {
        val keys = listOf("n_com.a", "n_com.gone", "pending", "seen")
        assertEquals(listOf("n_com.gone"), NotifCounter.staleKeys(keys, setOf("com.a")))
    }
}
