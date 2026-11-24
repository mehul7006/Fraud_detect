package com.fraudshield.app

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 18.3 / 18.4 / 18.5: compact list, decisions with every escape hatch, Play build never blocks ads, stats. */
class BlockListTest {
    private fun fsbl(version: Long, entries: Map<String, Int>): ByteArray {
        val rows = entries.entries.associate { BlockList.hash64(it.key) to it.value }.toSortedMap()
        val b = ByteArrayOutputStream()
        DataOutputStream(b).run {
            write("FSBL1".toByteArray()); writeLong(version); writeInt(rows.size)
            rows.forEach { (h, c) -> writeLong(h); writeByte(c) }
        }
        return b.toByteArray()
    }

    private val sample = mapOf("doubleclick.net" to BlockList.ADS, "app-measurement.com" to BlockList.TRACKERS,
        "sbi-kyc-update.top" to BlockList.MALWARE)
    private fun list() = BlockList.read(fsbl(5, sample).inputStream())

    @Test fun same_hash_as_the_python_builder() {
        // python: struct.unpack('>q', sha256(d).digest()[:8])[0]
        assertEquals(-8358137493662592906L, BlockList.hash64("doubleclick.net"))
        assertEquals(-6667114193848649307L, BlockList.hash64("example.com"))
    }

    @Test fun lookup_matches_the_name_and_its_parents_only() {
        val l = list()
        assertEquals(5L, l.version)
        assertEquals(BlockList.ADS, l.category("doubleclick.net"))
        assertEquals(BlockList.ADS, l.category("Stats.G.DoubleClick.net."))
        assertEquals(BlockList.TRACKERS, l.category("region1.app-measurement.com"))
        assertEquals(BlockList.MALWARE, l.category("login.sbi-kyc-update.top"))
        assertEquals(0, l.category("notdoubleclick.net"))
        assertEquals(0, l.category("net"))
        assertEquals(mapOf("ads" to 1, "trackers" to 1, "malware" to 1), l.counts())
    }

    @Test fun broken_files_are_refused() {
        val good = fsbl(1, sample)
        assertThrows(IllegalArgumentException::class.java) { BlockList.read("NOPE!".toByteArray().inputStream()) }
        val unsorted = good.copyOf().also { System.arraycopy(good, 17, it, 26, 9); System.arraycopy(good, 26, it, 17, 9) }
        assertThrows(IllegalArgumentException::class.java) { BlockList.read(unsorted.inputStream()) }
        assertThrows(IllegalArgumentException::class.java) { BlockList.read((good + byteArrayOf(0)).inputStream()) }
        assertThrows(Exception::class.java) { BlockList.read(good.copyOf(good.size - 3).inputStream()) }
    }

    @Test fun play_build_never_blocks_ads_web_build_does() {
        val play = BlockPolicy(adsAvailable = false).apply { list = list(); enabled = setOf(BlockList.ADS, BlockList.TRACKERS, BlockList.MALWARE) }
        assertEquals(0, play.decide("doubleclick.net", 10100, 0))
        assertEquals(BlockList.TRACKERS, play.decide("app-measurement.com", 10100, 0))
        assertEquals(BlockList.MALWARE, play.decide("sbi-kyc-update.top", 10100, 0))
        assertFalse(BlockList.ADS in BlockPolicy.defaults(false))
        val web = BlockPolicy(adsAvailable = true).apply { list = list() }
        assertEquals(BlockList.ADS, web.decide("pagead.doubleclick.net", 10100, 0))
    }

    @Test fun switches_and_escape_hatches() {
        val p = BlockPolicy(adsAvailable = true).apply { list = list() }
        p.enabled = setOf(BlockList.MALWARE)                       // category switch, live
        assertEquals(0, p.decide("doubleclick.net", 1, 0))
        assertEquals(BlockList.MALWARE, p.decide("sbi-kyc-update.top", 1, 0))
        p.enabled = BlockPolicy.defaults(true)
        p.pausedUntil = 1_000                                       // "pause 5 min"
        assertEquals(0, p.decide("doubleclick.net", 1, 999))
        assertEquals(BlockList.ADS, p.decide("doubleclick.net", 1, 1_000))
        p.allowedUids = setOf(10200)                                // allow one app
        assertEquals(0, p.decide("doubleclick.net", 10200, 2_000))
        assertEquals(BlockList.ADS, p.decide("doubleclick.net", 10201, 2_000))
        p.allowedSites = setOf("doubleclick.net")                   // allow one site (and its subdomains)
        assertEquals(0, p.decide("ad.doubleclick.net", 10201, 2_000))
        assertEquals(BlockList.TRACKERS, p.decide("app-measurement.com", 10201, 2_000))
    }

    @Test fun stats_per_app_category_and_top_domains() {
        val s = BlockStats.summarize(listOf(
            BlockStats.Row(10100, BlockList.ADS, "doubleclick.net", 30),
            BlockStats.Row(10100, BlockList.TRACKERS, "app-measurement.com", 5),
            BlockStats.Row(10200, BlockList.ADS, "doubleclick.net", 10),
            BlockStats.Row(-1, BlockList.MALWARE, "sbi-kyc-update.top", 1)))
        assertEquals(46, s.total)
        assertEquals(mapOf("ads" to 40, "trackers" to 5, "malware" to 1), s.byCategory)
        assertEquals(listOf(10100 to 35, 10200 to 10, -1 to 1), s.byApp)
        assertEquals(Triple("doubleclick.net", BlockList.ADS, 40), s.topDomains.first())
        assertEquals(46 * BlockStats.AVG_BYTES, s.estimatedBytesSaved)
    }

    /** 18.3 Done-when on the REAL list (built by ml/blocklists/build_blocklists.py; skipped where it is not built). */
    @Test fun real_list_memory_and_speed() {
        val f = listOf("../../../_devtools/blocklists/out/blocklists-web.bin", "../../_devtools/blocklists/out/blocklists-web.bin")
            .map(::File).firstOrNull { it.exists() }
        assumeTrue("real list not built here (run ml/blocklists/build_blocklists.py)", f != null)
        val t0 = System.nanoTime()
        val l = f!!.inputStream().use(BlockList::read)
        val loadMs = (System.nanoTime() - t0) / 1e6
        val bytes = l.size * 9L // LongArray + ByteArray, the only per-domain storage
        assertTrue("need >= 100k domains, got ${l.size}", l.size >= 100_000)
        assertTrue("memory ${bytes / 1e6} MB", bytes < 15_000_000)
        val names = (0 until 20_000).map { i -> if (i % 4 == 0) "region$i.app-measurement.com" else "host$i.cdn$i.example$i.co.in" }
        repeat(3) { names.forEach(l::category) } // warm-up
        val t1 = System.nanoTime()
        var hits = 0
        for (n in names) if (l.category(n) != 0) hits++
        val perLookupUs = (System.nanoTime() - t1) / 1e3 / names.size
        System.err.println("18.3 real list: ${l.size} domains ${l.counts()}, ${"%.1f".format(bytes / 1e6)} MB, load ${"%.0f".format(loadMs)} ms, lookup ${"%.2f".format(perLookupUs)} us, hits $hits")
        assertTrue("lookup $perLookupUs us", perLookupUs < 1000)
        assertEquals(BlockList.ADS, l.category("doubleclick.net"))
        assertEquals(BlockList.TRACKERS, l.category("app-measurement.com"))
        assertEquals(0, l.category("google.com"))
        assertEquals(0, l.category("onlinesbi.sbi"))
    }
}
