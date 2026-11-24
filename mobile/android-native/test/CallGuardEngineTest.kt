package com.fraudshield.app

import com.fraudshield.app.CallGuardEngine.Action
import com.fraudshield.app.CallGuardEngine.Input
import com.fraudshield.app.CallGuardEngine.Kind
import com.fraudshield.app.CallGuardEngine.Lists
import com.fraudshield.app.CallGuardEngine.Mode
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

/** 16.7 (+ 16.3 / 16.4 / 16.11 phone side): same answers as the server's CallGuardTest, >= 30 decision cases. */
class CallGuardEngineTest {
    // ---------------- canonical form + hash: the server's own vectors (backend CallGuardTest)
    @Test fun every_way_of_writing_an_indian_number_is_the_same_number() {
        for (s in listOf("9876543210", "+919876543210", "09876543210", "919876543210", "+91 98765 43210",
                "+91-98765-43210", "0091 98765 43210", "(+91) 98765.43210"))
            assertEquals(s, "+919876543210", CallNumbers.canonical(s))
        assertEquals("+14155550123", CallNumbers.canonical("+1 (415) 555-0123"))
        assertEquals("+923001234567", CallNumbers.canonical("0092 300 1234567"))
    }

    @Test fun short_codes_odd_lengths_and_junk_have_no_canonical_form() {
        for (s in listOf(null, "", "1930", "112", "98765", "+91987654321", "+9198765432100", "abc9876543210",
                "+1234567", "+1234567890123456", "123456789012", "+0123456789"))
            assertNull(s.toString(), CallNumbers.canonical(s))
    }

    @Test fun hash_matches_the_server() {
        val h = CallNumbers.hash("+919876543210")
        assertEquals("f3a47ce5ce3d4ca8ad15225a245b2759022f79489f5c62719b8c9490f7aab90e", h)
        assertEquals(h, CallNumbers.hash(CallNumbers.canonical("98765 43210")!!))
        assertEquals("f3a47", CallNumbers.prefix(h))
        assertEquals("9876543210", CallNumbers.indianNational("+919876543210"))
        assertNull(CallNumbers.indianNational("+14155550123"))
    }

    // ---------------- 16.3 rules: the server's 30 samples (CallGuardTest.thirty_samples) + its extra cases
    @Test fun thirty_rule_samples_same_as_server() {
        val r = NumberRules.DEFAULT
        val samples = listOf(
            "1600-300-123" to "IN_1600_SERVICE", "1600200456" to "IN_1600_SERVICE", "+91 1600 302 999" to "IN_1600_SERVICE",
            "01600300123" to "IN_1600_SERVICE", "911600300123" to "IN_1600_SERVICE",
            "1401234567" to "IN_140_PROMOTIONAL", "+91 140 123 4567" to "IN_140_PROMOTIONAL", "0140-1234567" to "IN_140_PROMOTIONAL",
            "+91 98765 4321" to "IN_ODD_LENGTH", "+91987654321099" to "IN_ODD_LENGTH", "+91 1600 30012" to "IN_ODD_LENGTH",
            "0091 12345" to "IN_ODD_LENGTH",
            "+4412345678901234" to "E164_ODD_LENGTH", "+44123456" to "INTERNATIONAL", "+1234567" to "E164_ODD_LENGTH",
            "+92 300 1234567" to "RISKY_COUNTRY", "0092 321 7654321" to "RISKY_COUNTRY", "+923001234567" to "RISKY_COUNTRY",
            "+1 415 555 0123" to "INTERNATIONAL", "+44 20 7946 0958" to "INTERNATIONAL", "+971 50 123 4567" to "INTERNATIONAL",
            "+880 1712 345678" to "INTERNATIONAL", "00977 98 0123 4567" to "INTERNATIONAL",
            "9876543210" to null, "+91 98765 43210" to null, "09876543210" to null, "011 2345 6789" to null,
            "1930" to null, "112" to null, "1800 180 1111" to null)
        assertEquals(30, samples.size)
        for ((n, want) in samples) assertEquals(n, want, r.classify(n)?.id)
        val r2 = NumberRules(r.version, r.rules, listOf("+92", " 234 ", "bad", "+92"))
        assertEquals(listOf("+234", "+92"), r2.risky)
        assertEquals("RISKY_COUNTRY", r2.classify("00234 801 234 5678")?.id)
        assertNull(r.classify(null))
    }

    /** The built-in rules are the server's seed file (same ids, order, numbers and labels). */
    @Test fun built_in_rules_equal_the_server_seed() {
        val seed = listOf("../../../backend/src/main/resources/seed/number-rules.json", "../../backend/src/main/resources/seed/number-rules.json")
            .map(::File).firstOrNull { it.exists() } ?: error("seed/number-rules.json not found from ${File(".").absolutePath}")
        val text = seed.readText()
        val ids = Regex("\"id\":\\s*\"(\\w+)\"").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(ids, NumberRules.DEFAULT.rules.map { it.id })
        assertTrue(text.contains("\"version\": \"${NumberRules.DEFAULT.version}\""))
        for (r in NumberRules.DEFAULT.rules) {
            assertTrue(r.id, text.contains("\"type\": \"${r.type}\""))
            assertTrue(r.id, text.contains(r.labelEn)); assertTrue(r.id, text.contains(r.labelHi))
            r.prefix?.let { assertTrue(r.id, text.contains("\"prefix\": \"$it\"")) }
        }
    }

    // ---------------- 16.7 decisions
    private val scamNumber = "+919811122233"
    private val scam = ScamNumberList().apply { restore(3, mapOf(CallNumbers.hash(scamNumber) to "SCAM")) }
    private fun lists(m: Mode, allow: Set<String> = emptySet(), block: Set<String> = emptySet()) =
        Lists(m, allow, block, scam, NumberRules.DEFAULT)
    private fun d(raw: String?, m: Mode, contact: Boolean = false, starred: Boolean = false, recent: Boolean = false,
                  allow: Set<String> = emptySet(), block: Set<String> = emptySet()) =
        CallGuardEngine.decide(Input(raw, contact, starred, recent), lists(m, allow, block))

    @Test fun known_people_are_never_touched_in_any_mode() {
        for (m in Mode.values()) {
            assertEquals(Action.ALLOW, d("9876543210", m, contact = true).action)                          // 1-4
            assertEquals(Action.ALLOW, d("9876543210", m, starred = true).action)                          // 5-8
            assertEquals(Action.ALLOW, d("9876543210", m, recent = true).action)                           // 9-12
            assertEquals(Action.ALLOW, d("+91 98765 43210", m, allow = setOf("+919876543210")).action)    // 13-16
            assertFalse(d("9876543210", m, contact = true).notify)
        }
        // even a crowd-reported number the user saved / called stays allowed
        assertEquals(Action.ALLOW, d(scamNumber, Mode.REJECT_UNKNOWN, contact = true).action)            // 17
        assertEquals(Action.ALLOW, d(scamNumber, Mode.BLOCK_SCAM, allow = setOf(scamNumber)).action)      // 18 "Not spam" undo
    }

    @Test fun scam_list_per_mode() {
        val one = d("098111 22233", Mode.LABEL_ONLY)
        assertEquals(Action.ALLOW, one.action); assertEquals(Kind.SCAM_LIST, one.kind); assertTrue(one.notify)  // 19
        assertEquals(Action.REJECT, d("+91 98111 22233", Mode.BLOCK_SCAM).action)                                // 20
        assertEquals(Action.REJECT, d("9811122233", Mode.BLOCK_SCAM_SILENCE_RISKY).action)                       // 21
        assertEquals(Action.REJECT, d("9811122233", Mode.REJECT_UNKNOWN).action)                                 // 22
        assertEquals("SCAM", d("9811122233", Mode.BLOCK_SCAM).scamCategory)
    }

    @Test fun foreign_and_odd_numbers_per_mode() {
        assertEquals(Action.ALLOW, d("+92 300 1234567", Mode.LABEL_ONLY).action)                       // 23
        assertEquals(Action.ALLOW, d("+92 300 1234567", Mode.BLOCK_SCAM).action)                       // 24
        assertEquals(Action.SILENCE, d("+92 300 1234567", Mode.BLOCK_SCAM_SILENCE_RISKY).action)       // 25
        assertEquals(Action.REJECT, d("+92 300 1234567", Mode.REJECT_UNKNOWN).action)                  // 26
        assertEquals(Action.SILENCE, d("+91 98765 4321", Mode.BLOCK_SCAM_SILENCE_RISKY).action)        // 27 odd length
        val intl = d("+1 415 555 0123", Mode.BLOCK_SCAM_SILENCE_RISKY)
        assertEquals(Action.ALLOW, intl.action); assertEquals(Kind.RULE_LABEL, intl.kind)              // 28
        assertEquals(Action.REJECT, d("+1 415 555 0123", Mode.REJECT_UNKNOWN).action)                  // 29
    }

    @Test fun unknown_callers_only_cut_in_mode_4() {
        for (m in listOf(Mode.LABEL_ONLY, Mode.BLOCK_SCAM, Mode.BLOCK_SCAM_SILENCE_RISKY)) {
            val x = d("9123456780", m); assertEquals(Action.ALLOW, x.action); assertEquals(Kind.UNKNOWN, x.kind) // 30-32
        }
        assertEquals(Action.REJECT, d("9123456780", Mode.REJECT_UNKNOWN).action)                       // 33
        assertEquals(Action.REJECT, d("", Mode.REJECT_UNKNOWN).action)                                 // 34 hidden
        assertEquals(Action.ALLOW, d(null, Mode.BLOCK_SCAM).action)                                    // 35 hidden
        assertEquals(Kind.HIDDEN, d("Private", Mode.BLOCK_SCAM).kind)
    }

    @Test fun never_blocked_service_codes_and_trai_series() {
        for (m in Mode.values()) for (n in listOf("112", "100", "1930", "108", "1800 180 1111", "*121#")) {
            if (n == "*121#") continue
            val x = d(n, m); assertEquals("$n $m", Action.ALLOW, x.action); assertFalse(x.notify)      // 36-55
        }
        for (m in Mode.values()) for (n in listOf("1600-300-123", "+91 140 123 4567")) {
            val x = d(n, m, block = setOf(CallNumbers.canonical(n)!!))
            assertEquals("$n $m", Action.ALLOW, x.action); assertEquals(Kind.PROTECTED_SERIES, x.kind)  // 56-63
        }
        // a crowd report can never put a 1600 number on the list - and even if it did, it would still ring
        val listed = ScamNumberList().apply { restore(1, mapOf(CallNumbers.hash("+911600300123") to "SCAM")) }
        assertEquals(Action.ALLOW, CallGuardEngine.decide(Input("1600300123"), Lists(Mode.REJECT_UNKNOWN, scam = listed)).action) // 64
    }

    @Test fun user_block_list_wins_in_every_mode() {
        for (m in Mode.values()) {
            val x = d("9123456780", m, block = setOf("+919123456780"))
            assertEquals(Action.REJECT, x.action); assertEquals(Kind.USER_BLOCKED, x.kind)              // 65-68
        }
    }

    @Test fun decision_is_fast() {
        val l = lists(Mode.BLOCK_SCAM_SILENCE_RISKY)
        val numbers = (0 until 10_000).map { "98${(10_000_000 + it * 37) % 100_000_000}".padEnd(10, '1') }
        val t0 = System.nanoTime()
        for (n in numbers) CallGuardEngine.decide(Input(n), l)
        val perCallMs = (System.nanoTime() - t0) / 1e6 / numbers.size
        assertTrue("per call $perCallMs ms", perCallMs < 5.0) // budget 500 ms; JVM cold ~0.01 ms
    }

    // ---------------- 16.4 signed delta (same canonical text as NumberReputationService.canonical)
    @Test fun signed_delta_applies_and_tampering_is_rejected() {
        assertEquals("fraudshield-numbers-v1\n7\ntrue\naa:SCAM,bb:SPAM\n",
            ScamNumberList.canonical(7, true, listOf("bb" to "SPAM", "aa" to "SCAM"), emptyList()))
        assertEquals("fraudshield-numbers-v1\n9\nfalse\n\ncc,dd", ScamNumberList.canonical(9, false, emptyList(), listOf("dd", "cc")))

        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val pub = Base64.getEncoder().encodeToString(kp.public.encoded)
        fun sign(t: String) = Base64.getEncoder().encodeToString(
            Signature.getInstance("SHA256withECDSA").run { initSign(kp.private); update(t.toByteArray()); sign() })
        val h1 = CallNumbers.hash("+919811122233"); val h2 = CallNumbers.hash("+919000000001")
        val added = listOf(h1 to "SCAM", h2 to "SPAM")
        val list = ScamNumberList()
        assertTrue(list.applyDelta(5, true, added, emptyList(), sign(ScamNumberList.canonical(5, true, added, emptyList())), pub))
        assertEquals(2, list.size()); assertEquals("SPAM", list.categoryOf(h2)); assertEquals(5, list.version)
        // tampered: category changed after signing
        val bad = listOf(h1 to "SCAM", h2 to "TELEMARKETING")
        assertFalse(list.applyDelta(6, false, bad, emptyList(), sign(ScamNumberList.canonical(6, false, added, emptyList())), pub))
        assertEquals("SPAM", list.categoryOf(h2)); assertEquals(5, list.version)
        // wrong key (the real app key) rejects a test-signed list
        assertFalse(ScamNumberList().applyDelta(5, true, added, emptyList(), sign(ScamNumberList.canonical(5, true, added, emptyList()))))
        // removal delta
        assertTrue(list.applyDelta(7, false, emptyList(), listOf(h1), sign(ScamNumberList.canonical(7, false, emptyList(), listOf(h1))), pub))
        assertNull(list.categoryOf(h1)); assertEquals(1, list.size())
    }

    // ---------------- 16.11 labels: 10 sample numbers
    @Test fun ten_caller_labels() {
        val r = NumberRules.DEFAULT
        fun lab(raw: String?, m: Mode = Mode.BLOCK_SCAM, contact: String? = null, sys: String? = null, hi: Boolean = false) =
            CallerLabeler.label(CallGuardEngine.decide(Input(raw, contact = contact != null), lists(m)), contact, sys, r, raw, hi)
        assertEquals("Mummy", lab("9876543210", contact = "Mummy"))                                                  // 1 contact wins
        assertEquals("Reported as fraud by many people", lab("9811122233"))                                         // 2
        assertEquals("Call from abroad (+92) - government offices, banks and police never call you from a foreign number", lab("+92 300 1234567")) // 3
        assertEquals("International call (+1)", lab("+1 415 555 0123"))                                            // 4
        assertEquals("International call (+971)", lab("+971 50 123 4567"))                                         // 5
        assertTrue(lab("1600-300-123").startsWith("Official service call (1600 number)"))                           // 6
        assertEquals("Unusual number: an Indian number has 10 digits after +91", lab("+91 98765 4321"))           // 7
        assertEquals("ACME Courier", lab("9123456780", sys = "ACME Courier"))                                       // 8 system name
        assertEquals("Unknown number", lab("9123456780"))                                                           // 9
        assertEquals("अंतरराष्ट्रीय कॉल (+44)", lab("+44 20 7946 0958", hi = true))                                    // 10 Hindi
        assertEquals("Hidden number", lab(""))
        assertEquals("local…", CallNumbers.redacted("9876543210")); assertEquals("+92…", CallNumbers.redacted("+923001234567"))
    }
}
