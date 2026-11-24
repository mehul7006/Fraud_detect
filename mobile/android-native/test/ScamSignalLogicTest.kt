package com.fraudshield.app

import com.fraudshield.app.ScamSignalLogic.Kind
import com.fraudshield.app.ScamSignalLogic.Level
import org.junit.Assert.*
import org.junit.Test

/** 15.2 / 15.4: live-scam correlation on the phone (same rule as ScamSessionService) + privacy gates. */
class ScamSignalLogicTest {
    private val remote = setOf("com.anydesk.anydeskandroid")
    private val bank = ScamSignalLogic.BANKING
    private val t0 = 1_700_000_000_000L
    private fun s(sec: Int) = t0 + sec * 1000L

    @Test fun four_signals_in_the_window_are_critical_once() {
        val l = ScamSignalLogic()
        l.onCallStarted(t0, unknownIncoming = true)
        assertEquals(Kind.REMOTE_ACCESS_APP, l.onForeground(s(30), "com.anydesk.anydeskandroid", remote, bank))
        assertEquals(Kind.BANKING_APP, l.onForeground(s(60), "com.phonepe.app", remote, bank))
        assertFalse("not yet: the call is still short", l.toSend(s(60)).isNotEmpty())
        assertTrue("OTP during a call -> share guard", l.onOtp(s(90)))
        assertEquals(Level.NONE, l.level(s(90)))
        l.tick(s(181))
        assertEquals(Level.CRITICAL, l.level(s(181)))
        assertEquals(Level.CRITICAL, l.newAlert(s(181)))
        assertNull("same level is not alerted twice", l.newAlert(s(185)))
        assertEquals(Kind.values().toList(), l.toSend(s(181)))
        assertEquals(181, l.callSeconds(s(181)))
    }

    @Test fun call_plus_screen_sharing_plus_one_more_is_a_warning() {
        val l = ScamSignalLogic()
        l.onCallStarted(t0, true)
        l.onForeground(s(10), "com.anydesk.anydeskandroid", remote, bank)
        l.tick(s(200))
        assertEquals(Level.NONE, l.level(s(200)))
        l.onOtp(s(210))
        assertEquals(Level.WARNING, l.level(s(210)))
        assertEquals(Level.WARNING, l.newAlert(s(210)))
        l.onForeground(s(220), "com.sbi.lotusintouch", remote, bank)
        assertEquals("rising level alerts again", Level.CRITICAL, l.newAlert(s(220)))
    }

    @Test fun without_screen_sharing_three_signals_stay_quiet() {
        val l = ScamSignalLogic()
        l.onCallStarted(t0, true); l.tick(s(200))
        l.onForeground(s(201), "com.phonepe.app", remote, bank); l.onOtp(s(202))
        assertEquals(Level.NONE, l.level(s(202)))
        assertNull(l.newAlert(s(202)))
    }

    @Test fun known_caller_or_outgoing_call_records_nothing() {
        val l = ScamSignalLogic()
        l.onCallStarted(t0, unknownIncoming = false)   // saved contact / outgoing
        assertNull(l.onForeground(s(10), "com.anydesk.anydeskandroid", remote, bank))
        assertNull(l.onForeground(s(20), "com.phonepe.app", remote, bank))
        assertTrue("OTP share guard still warns on ANY call", l.onOtp(s(30)))
        l.tick(s(400))
        assertTrue(l.seenKinds(s(400)).isEmpty())
        assertTrue(l.toSend(s(400)).isEmpty())
    }

    @Test fun ordinary_banking_without_a_call_leaves_no_trace() {
        val l = ScamSignalLogic()
        assertNull(l.onForeground(t0, "com.phonepe.app", remote, bank))
        assertFalse("no call -> no OTP warning", l.onOtp(s(5)))
        assertTrue(l.seenKinds(s(5)).isEmpty())
    }

    @Test fun signals_after_the_call_count_for_ten_minutes_then_expire() {
        val l = ScamSignalLogic()
        l.onCallStarted(t0, true)
        l.onCallEnded(s(200))                                 // long unknown call (counted at hang-up)
        assertTrue(l.armed(s(500)))
        l.onForeground(s(300), "com.anydesk.anydeskandroid", remote, bank)
        l.onForeground(s(400), "com.phonepe.app", remote, bank)
        assertFalse("call over: no share guard", l.onOtp(s(500)))
        assertEquals(Level.CRITICAL, l.level(s(500)))
        assertFalse(l.armed(s(200 + 601)))
        assertNull(l.onForeground(s(900), "com.phonepe.app", remote, bank))
        l.expire(s(2000))
        assertTrue(l.seenKinds(s(2000)).isEmpty())
    }

    @Test fun short_unknown_call_never_sends() {
        val l = ScamSignalLogic()
        l.onCallStarted(t0, true)
        l.onForeground(s(5), "com.anydesk.anydeskandroid", remote, bank)
        l.onCallEnded(s(60))
        assertTrue(l.toSend(s(61)).isEmpty())
        assertFalse(Kind.LONG_UNKNOWN_CALL in l.seenKinds(s(61)))
    }

    @Test fun resend_only_after_half_a_window() {
        val l = ScamSignalLogic()
        l.onCallStarted(t0, true); l.tick(s(180))
        val first = l.toSend(s(180)); first.forEach { l.markSent(it, s(180)) }
        assertEquals(listOf(Kind.LONG_UNKNOWN_CALL), first)
        l.tick(s(200))
        assertTrue(l.toSend(s(200)).isEmpty())
        l.tick(s(481))
        assertEquals(listOf(Kind.LONG_UNKNOWN_CALL), l.toSend(s(481)))
    }

    @Test fun otp_text_detection_never_needs_the_code_kept() {
        listOf("482913 is your OTP for Rs 1,250 at Amazon. Do not share",
            "Your one-time password is 7731", "Use verification code 55120 to sign in",
            "आपका ओटीपी 839201 है", "SBI: OTP for txn of Rs 5000 is 120934").forEach { assertTrue(it, ScamSignalLogic.isOtpText(it)) }
        listOf("Your order 4029-118 has shipped", "OTP verification pending", "Call 18001234 for help",
            "Meeting at 1530 in room 4").forEach { assertFalse(it, ScamSignalLogic.isOtpText(it)) }
    }
}
