package com.fraudshield.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 8.2: access token lifetime and millisecond issue time. */
class JwtServiceTest {
    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret-123";

    @Test
    void validTokenCarriesUserRoleAndMillisecondIssueTime() {
        JwtService j = new JwtService(SECRET, 15);
        long before = System.currentTimeMillis();
        JwtService.Access a = j.verify(j.create(7L, "ADMIN"));
        assertEquals(7L, a.userId());
        assertEquals("ADMIN", a.role());
        assertTrue(a.issuedAt().toEpochMilli() >= before);
        assertEquals(900, j.ttlSeconds());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService j = new JwtService(SECRET, 0);
        assertNull(j.verify(j.create(7L)));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = new JwtService("another-secret-another-secret-another-secret-12345", 15).create(7L);
        assertNull(new JwtService(SECRET, 15).verify(foreign));
    }
}
