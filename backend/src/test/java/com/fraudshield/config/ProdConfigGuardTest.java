package com.fraudshield.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProdConfigGuardTest {
    private static final String STRONG = "k8Qz1vN0pX7mW3rT9yB5uH2jL6dF4gA8sC1eV7bZ";
    private final ProdConfigGuard guard = new ProdConfigGuard();

    private MockEnvironment prodEnv(String jwt, String dbPw) {
        MockEnvironment e = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://h/db")
                .withProperty("spring.datasource.username", "u");
        if (dbPw != null) e.setProperty("spring.datasource.password", dbPw);
        if (jwt != null) e.setProperty("fraudshield.jwt.secret", jwt);
        e.setActiveProfiles("prod");
        return e;
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(prodEnv("short", "pw-ok-123"), null));
    }

    @Test
    void rejectsDevAndDefaultSecrets() {
        assertThrows(IllegalStateException.class, () -> ProdConfigGuard.check("dev-only-secret-dev-only-secret-dev-only-secret", "pw-ok-123"));
        assertThrows(IllegalStateException.class, () -> ProdConfigGuard.check("change-me-change-me-change-me-change-me", "pw-ok-123"));
    }

    @Test
    void rejectsDevOrEmptyDbPassword() {
        assertThrows(IllegalStateException.class, () -> ProdConfigGuard.check(STRONG, "fraud"));
        assertThrows(IllegalStateException.class, () -> ProdConfigGuard.check(STRONG, ""));
    }

    @Test
    void namesEveryMissingVariable() {
        MockEnvironment e = new MockEnvironment();   // nothing set
        e.setActiveProfiles("prod");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(e, null));
        for (String n : new String[]{"DB_URL", "DB_USER", "DB_PASSWORD", "JWT_SECRET"}) assertTrue(ex.getMessage().contains(n), ex.getMessage());
    }

    @Test
    void unresolvedPlaceholderCountsAsMissing() {
        MockEnvironment e = prodEnv("${JWT_SECRET}", "pw-ok-123");
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(e, null));
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void acceptsStrongConfigInProd() {
        assertDoesNotThrow(() -> guard.postProcessEnvironment(prodEnv(STRONG, "pw-ok-123"), null));
    }

    @Test
    void doesNothingOutsideProd() {
        MockEnvironment dev = new MockEnvironment();   // no prod profile, nothing set, weak values would be fine
        assertDoesNotThrow(() -> guard.postProcessEnvironment(dev, null));
    }
}
