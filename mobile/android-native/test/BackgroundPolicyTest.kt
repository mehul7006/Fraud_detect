package com.fraudshield.app

import com.fraudshield.app.BackgroundPolicy.Action
import org.junit.Assert.*
import org.junit.Test

class BackgroundPolicyTest {
    private fun act(host: String? = "evil.example", pkg: String? = "com.whatsapp", disabled: Set<String> = emptySet(),
                    trusted: List<String> = emptyList(), fg: Boolean = false, session: Boolean = true) =
        BackgroundPolicy.forLink(host, pkg, disabled, trusted, fg, session)

    @Test fun appClosedWithSession_scansInBackground() = assertEquals(Action.SCAN, act())
    @Test fun appOnScreen_leavesItToTheApp() = assertEquals(Action.QUEUE, act(fg = true))
    @Test fun noSession_queuesSoNothingIsLost() = assertEquals(Action.QUEUE, act(session = false))
    @Test fun messengerTurnedOff_skips() = assertEquals(Action.SKIP, act(disabled = setOf("com.whatsapp")))
    @Test fun trustedSiteAndSubdomain_skip() {
        assertEquals(Action.SKIP, act(host = "sbi.co.in", trusted = listOf("sbi.co.in")))
        assertEquals(Action.SKIP, act(host = "online.SBI.co.in", trusted = listOf("sbi.co.in")))
    }
    @Test fun lookAlikeIsNotTrusted() {
        assertEquals(Action.SCAN, act(host = "sbi.co.in.evil.example", trusted = listOf("sbi.co.in")))
        assertEquals(Action.SCAN, act(host = "notsbi.co.in", trusted = listOf("sbi.co.in")))
    }
    @Test fun hostOfLinksWithAndWithoutScheme() {
        assertEquals("evil.example", BackgroundPolicy.hostOf("https://Evil.Example/login?x=1"))
        assertEquals("bit.ly", BackgroundPolicy.hostOf("bit.ly/abc"))
        assertNull(BackgroundPolicy.hostOf("http://"))
    }
    @Test fun safeVerdictStaysQuiet_badOnesAlert() {
        assertNull(BackgroundPolicy.alertFor("SAFE", "https://a", emptyList()))
        assertEquals("Dangerous link!", BackgroundPolicy.alertFor("MALICIOUS", "https://a", listOf("phishing"))!!.first)
        assertTrue(BackgroundPolicy.alertFor("SUSPICIOUS", "https://a", listOf("new domain"))!!.second.contains("new domain"))
    }
    @Test fun failuresThatAreWorthRetrying() {
        listOf(0, 401, 429, 500, 503).forEach { assertTrue("$it", BackgroundPolicy.queueOnFailure(it)) }
        listOf(400, 403, 404, 422).forEach { assertFalse("$it", BackgroundPolicy.queueOnFailure(it)) }
    }
}
