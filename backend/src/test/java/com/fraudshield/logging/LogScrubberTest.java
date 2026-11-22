package com.fraudshield.logging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 8.4 */
class LogScrubberTest {
    @Test
    void emailsAreMasked() {
        assertEquals("duplicate key (email)=(<email>)", LogScrubber.scrub("duplicate key (email)=(rahul.k+test@gmail.com)"));
    }

    @Test
    void urlQueryStringsAreDropped_hostAndPathKept() {
        assertEquals("scan https://evil.example/login?<query> failed",
                LogScrubber.scrub("scan https://evil.example/login?token=abc&phone=9876543210 failed"));
        assertEquals("http://x.example/a?<query>", LogScrubber.scrub("http://x.example/a#otp=1234"));
        assertEquals("https://sbi.co.in/personal", LogScrubber.scrub("https://sbi.co.in/personal"));
    }

    @Test
    void tokensAndPhoneNumbersAreMasked() {
        assertEquals("Authorization: Bearer <token>", LogScrubber.scrub("Authorization: Bearer abcdEFGH12345678.xyz"));
        assertEquals("t=<jwt>", LogScrubber.scrub("t=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3In0.c2lnbmF0dXJlLXZhbHVl"));
        assertEquals("call <phone> or <phone>", LogScrubber.scrub("call +91 98765 43210 or 9876543210"));
    }

    @Test
    void harmlessTextIsUntouched() {
        String s = "App reputation: 511 apps seeded; HTTP 429; id 12345; sha256 e3b0c44298fc1c149afbf4c8996fb924";
        assertEquals(s, LogScrubber.scrub(s));
    }
}
