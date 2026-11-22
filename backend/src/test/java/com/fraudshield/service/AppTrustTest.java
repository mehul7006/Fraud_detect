package com.fraudshield.service;

import com.fraudshield.service.AppTrust.Result;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 6.2: a known-good package name alone is not enough to be trusted. */
class AppTrustTest {
    static final String REAL = "aa".repeat(32);
    static final String OTHER = "bb".repeat(32);
    static final String PLAY = "com.android.vending";

    @Test void matchingCertificateIsGenuine() {
        assertEquals(Result.GENUINE, AppTrust.check(REAL, REAL, null));
        assertEquals(Result.GENUINE, AppTrust.check(REAL, REAL, "com.some.sideloader"), "cert wins over installer");
    }

    @Test void wrongCertificateIsFakeEvenFromPlay() {
        assertEquals(Result.FAKE, AppTrust.check(REAL, OTHER, PLAY));
    }

    @Test void rotatedCertificatesAnyMatchIsGenuine() {
        assertEquals(Result.GENUINE, AppTrust.check(OTHER + "," + REAL, REAL, null));
        assertEquals(Result.GENUINE, AppTrust.check(REAL, OTHER + ";" + REAL, null));
    }

    @Test void unknownCertificateTrustsOnlyGooglePlayInstalls() {
        assertEquals(Result.GENUINE, AppTrust.check(null, OTHER, PLAY));
        assertEquals(Result.UNVERIFIED, AppTrust.check(null, OTHER, "com.android.packageinstaller"));
        assertEquals(Result.UNVERIFIED, AppTrust.check(null, null, null), "old client sends nothing");
    }

    @Test void knownCertificateButClientSentNoneIsNotTrustedByName() {
        assertEquals(Result.UNVERIFIED, AppTrust.check(REAL, null, PLAY));
    }

    @Test void certificateFormatsAreNormalised() {
        String colons = String.join(":", REAL.toUpperCase().split("(?<=\\G..)"));
        assertEquals(Result.GENUINE, AppTrust.check(colons, REAL, null));
        assertEquals(REAL, AppTrust.normalise(colons));
        assertTrue(AppTrust.validCerts(colons));
        assertFalse(AppTrust.validCerts("abc"));
        assertFalse(AppTrust.validCerts("zz".repeat(32)));
        assertNull(AppTrust.normalise(" "));
    }
}
