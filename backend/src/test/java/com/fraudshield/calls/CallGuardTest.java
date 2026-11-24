package com.fraudshield.calls;

import static org.junit.jupiter.api.Assertions.*;

import com.fraudshield.domain.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 16.1 / 16.3 / 16.4 / 16.5 pure logic: canonical number + hash, TRAI rules, vote weights, signed text. */
class CallGuardTest {
    @Test
    void every_way_of_writing_an_indian_number_is_the_same_number() {
        for (String s : List.of("9876543210", "+919876543210", "09876543210", "919876543210", "+91 98765 43210",
                "+91-98765-43210", "0091 98765 43210", "(+91) 98765.43210"))
            assertEquals("+919876543210", PhoneNumbers.canonical(s), s);
        assertEquals("+14155550123", PhoneNumbers.canonical("+1 (415) 555-0123"));
        assertEquals("+923001234567", PhoneNumbers.canonical("0092 300 1234567"));
    }

    @Test
    void short_codes_odd_lengths_and_junk_have_no_canonical_form() {
        for (String s : new String[] {null, "", "1930", "112", "98765", "+91987654321", "+9198765432100", "abc9876543210",
                "+1234567", "+1234567890123456", "123456789012", "+0123456789"})
            assertNull(PhoneNumbers.canonical(s), String.valueOf(s));
    }

    @Test
    void hash_is_sha256_hex_of_the_canonical_text() {
        // echo -n "+919876543210" | sha256sum
        String h = PhoneNumbers.hash("+919876543210");
        assertTrue(PhoneNumbers.isHash(h));
        assertEquals(h, PhoneNumbers.hash(PhoneNumbers.canonical("98765 43210")));
        assertEquals("f3a47ce5ce3d4ca8ad15225a245b2759022f79489f5c62719b8c9490f7aab90e", h);
        assertFalse(PhoneNumbers.isHash("ABC"));
        assertEquals("9876543210", PhoneNumbers.indianNational("+919876543210"));
        assertNull(PhoneNumbers.indianNational("+14155550123"));
    }

    @Test
    void trai_series_odd_lengths_and_foreign_codes() {
        NumberRuleService r = new NumberRuleService("+92, 234 ,bad,+92");
        assertEquals("IN_1600_SERVICE", r.classify("1600-300-123"));
        assertEquals("IN_1600_SERVICE", r.classify("+91 1600300123"));
        assertEquals("IN_140_PROMOTIONAL", r.classify("1401234567"));
        assertEquals("IN_ODD_LENGTH", r.classify("+91 98765 4321"));
        assertEquals("IN_ODD_LENGTH", r.classify("+91987654321099"));
        assertEquals("E164_ODD_LENGTH", r.classify("+4412345678901234"));
        assertEquals("RISKY_COUNTRY", r.classify("+92 300 1234567"));
        assertEquals("RISKY_COUNTRY", r.classify("00234 801 234 5678"));
        assertEquals("INTERNATIONAL", r.classify("+1 415 555 0123"));
        assertNull(r.classify("9876543210"), "an ordinary Indian mobile matches no rule");
        assertNull(r.classify("1930"));
        assertNull(r.classify(null));
        List<String> codes = new java.util.ArrayList<>();
        r.rules().get("riskyCountryCodes").forEach(n -> codes.add(n.asText()));
        assertEquals(List.of("+234", "+92"), codes, "configurable, cleaned, de-duplicated");
        // every rule cites a source that exists, with a URL
        for (var rule : r.rules().get("rules"))
            for (var s : rule.get("sources")) assertTrue(r.rules().get("sources").get(s.asText()).get("url").asText().startsWith("https://"), s.asText());
        assertTrue(NumberRuleService.isProtected("+911600300123"));
        assertTrue(NumberRuleService.isProtected("+911401234567"));
        assertFalse(NumberRuleService.isProtected("+919876543210"));
        assertFalse(NumberRuleService.isProtected(null));
    }

    /** 16.3 done-when: 30 sample numbers classified as expected. */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "1600-300-123, IN_1600_SERVICE", "1600200456, IN_1600_SERVICE", "+91 1600 302 999, IN_1600_SERVICE",
            "01600300123, IN_1600_SERVICE", "911600300123, IN_1600_SERVICE",
            "1401234567, IN_140_PROMOTIONAL", "+91 140 123 4567, IN_140_PROMOTIONAL", "0140-1234567, IN_140_PROMOTIONAL",
            "+91 98765 4321, IN_ODD_LENGTH", "+91987654321099, IN_ODD_LENGTH", "+91 1600 30012, IN_ODD_LENGTH", "0091 12345, IN_ODD_LENGTH",
            "+4412345678901234, E164_ODD_LENGTH", "+44123456, INTERNATIONAL", "+1234567, E164_ODD_LENGTH",
            "+92 300 1234567, RISKY_COUNTRY", "0092 321 7654321, RISKY_COUNTRY", "+923001234567, RISKY_COUNTRY",
            "+1 415 555 0123, INTERNATIONAL", "+44 20 7946 0958, INTERNATIONAL", "+971 50 123 4567, INTERNATIONAL",
            "+880 1712 345678, INTERNATIONAL", "00977 98 0123 4567, INTERNATIONAL",
            "9876543210, NONE", "+91 98765 43210, NONE", "09876543210, NONE", "011 2345 6789, NONE",
            "1930, NONE", "112, NONE", "1800 180 1111, NONE"})
    void thirty_samples(String number, String expected) {
        NumberRuleService r = new NumberRuleService("+92");
        String want = "NONE".equals(expected) ? null : expected;
        assertEquals(want, r.classify(number), number);
    }

    @Test
    void new_accounts_count_less() {
        Instant now = Instant.now();
        assertEquals(0.2, NumberReputationService.weight(user(now.minus(Duration.ofHours(2))), now));
        assertEquals(0.5, NumberReputationService.weight(user(now.minus(Duration.ofDays(3))), now));
        assertEquals(1.0, NumberReputationService.weight(user(now.minus(Duration.ofDays(30))), now));
    }

    @Test
    void canonical_signed_text() {
        assertEquals("fraudshield-numbers-v1\n7\ntrue\naa:SCAM,bb:SPAM\n",
                NumberReputationService.canonical(7, true, List.of(new NumberReputationService.Entry("aa", "SCAM"),
                        new NumberReputationService.Entry("bb", "SPAM")), List.of()));
        assertEquals("fraudshield-numbers-v1\n9\nfalse\n\ncc,dd", NumberReputationService.canonical(9, false, List.of(), List.of("cc", "dd")));
    }

    private static User user(Instant created) {
        User u = new User("x@t.com", "h");
        ReflectionTestUtils.setField(u, "createdAt", created);
        return u;
    }
}
