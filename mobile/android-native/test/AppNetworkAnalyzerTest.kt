package com.fraudshield.app

import org.junit.Assert.*
import org.junit.Test

/** 13.5: an app talking to 10 ad servers is flagged, a normal app is not; malware hosts always count. */
class AppNetworkAnalyzerTest {
    private val cats = mapOf("doubleclick.net" to "ads", "applovin.com" to "ads", "unityads.unity3d.com" to "ads",
        "app-measurement.com" to "analytics", "appsflyer.com" to "tracker", "evil-dropper.xyz" to "malware")

    private fun cat(d: String) = DomainGuard.parents(d).firstNotNullOfOrNull { cats[it] }

    @Test fun ad_heavy_app_is_flagged() {
        val domains = (1..10).associate { "ad$it.doubleclick.net" to 3 } + mapOf("api.mygame.com" to 20)
        val r = AppNetworkAnalyzer.analyze(domains, ::cat)
        assertEquals(10, r.ads)
        assertEquals(listOf("MANY_AD_DOMAINS"), r.codes)
    }

    @Test fun normal_app_is_not_flagged() {
        val r = AppNetworkAnalyzer.analyze(mapOf("api.mybank.in" to 50, "cdn.mybank.in" to 9, "app-measurement.com" to 2), ::cat)
        assertTrue(r.codes.isEmpty())
        assertEquals(1, r.trackers)
    }

    @Test fun malware_host_is_always_reported() {
        val r = AppNetworkAnalyzer.analyze(mapOf("cdn.evil-dropper.xyz" to 1), ::cat)
        assertEquals(listOf("MALWARE_DOMAIN"), r.codes)
    }

    @Test fun canonical_text_matches_the_server() {
        assertEquals("fraudshield-domains-v1\n7\ntrue\na.com:ads,b.com:malware\nc.com",
            AppNetworkAnalyzer.canonical(7, true, listOf("a.com" to "ads", "b.com" to "malware"), listOf("c.com")))
    }
}
