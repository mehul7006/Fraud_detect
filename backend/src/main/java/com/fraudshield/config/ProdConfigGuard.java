package com.fraudshield.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs before the application context is built. In the "prod" profile it refuses to start when required
 * environment variables are missing or when secrets are weak/development defaults.
 * Registered in META-INF/spring.factories.
 */
public class ProdConfigGuard implements EnvironmentPostProcessor, Ordered {

    /** property -> environment variable name shown to the operator */
    private static final Map<String, String> REQUIRED = Map.of(
            "spring.datasource.url", "DB_URL",
            "spring.datasource.username", "DB_USER",
            "spring.datasource.password", "DB_PASSWORD",
            "fraudshield.jwt.secret", "JWT_SECRET");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        if (!env.acceptsProfiles(Profiles.of("prod"))) return;

        List<String> missing = new ArrayList<>();
        REQUIRED.forEach((prop, envName) -> {
            try {
                String v = env.getProperty(prop);
                if (v == null || v.isBlank()) missing.add(envName);
            } catch (IllegalArgumentException unresolvedPlaceholder) {
                missing.add(envName);
            }
        });
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Cannot start in prod: missing environment variables " + String.join(", ", missing.stream().sorted().toList()));
        }
        check(env.getProperty("fraudshield.jwt.secret"), env.getProperty("spring.datasource.password"));
    }

    public static void check(String jwtSecret, String dbPassword) {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters in prod");
        }
        String low = jwtSecret.toLowerCase();
        if (low.startsWith("dev-only") || low.startsWith("change-me")) {
            throw new IllegalStateException("JWT_SECRET is a development/default value - set a real secret in prod");
        }
        if (dbPassword == null || dbPassword.isBlank() || dbPassword.equals("fraud")) {
            throw new IllegalStateException("DB_PASSWORD is empty or a development default in prod");
        }
    }

    /** After config files (application-prod.yml) are loaded. */
    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
