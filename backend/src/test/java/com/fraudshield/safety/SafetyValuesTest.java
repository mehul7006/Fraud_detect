package com.fraudshield.safety;

import static org.junit.jupiter.api.Assertions.*;

import com.fraudshield.safety.CrowdItem.Type;
import org.junit.jupiter.api.Test;

/** One normalisation + hash rule, the same on the phone and the server. */
class SafetyValuesTest {
    @Test
    void upiIdsAreCaseInsensitive() {
        assertEquals(SafetyValues.hash(Type.UPI, "Name.Scam@YBL"), SafetyValues.hash(Type.UPI, "  name.scam@ybl "));
        // known vector so the app can check its own implementation
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", SafetyValues.sha256(""));
    }

    @Test
    void linksAreReducedToTheHost() {
        assertEquals("sbi-kyc.top", SafetyValues.normalise(Type.LINK, "HTTPS://WWW.SBI-KYC.top:8443/login?x=1"));
        assertEquals("evil.top", SafetyValues.normalise(Type.LINK, "https://sbi.co.in@evil.top/a"));
        assertEquals("fleepkart.com", SafetyValues.normalise(Type.LINK, "fleepkart.com."));
    }

    @Test
    void numbersLoseCountryCodeAndTrunkZero() {
        assertEquals("9876543210", SafetyValues.number("+91 98765 43210"));
        assertEquals("9876543210", SafetyValues.number("098765-43210"));
        assertEquals("9876543210", SafetyValues.number("0091 9876543210"));
        assertEquals("18001234", SafetyValues.number("1800 1234"));
        assertEquals("18002026161", SafetyValues.number("1800 202 6161"));
        assertEquals("1930", SafetyValues.number("1930"));
        assertTrue(SafetyValues.isMobile("9876543210"));
        assertFalse(SafetyValues.isMobile("18002026161"));
        assertFalse(SafetyValues.isMobile("1204456456"));
    }

    @Test
    void hashFormat() {
        assertTrue(SafetyValues.isHash(SafetyValues.hash(Type.LINK, "1930.in")));
        assertFalse(SafetyValues.isHash("ABC"));
        assertFalse(SafetyValues.isHash(SafetyValues.hash(Type.UPI, "x").toUpperCase()));
    }
}
