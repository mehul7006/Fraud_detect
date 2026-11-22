package com.fraudshield;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 8.3: security headers on every response; CORS closed except for named web origins. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "fraudshield.cors.allowed-origins=https://admin.fraudshield.example")
class SecurityHeadersIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;

    @Test
    void everyApiResponseCarriesTheSecurityHeaders_evenErrors() throws Exception {
        mvc.perform(get("/api/v1/threats")) // 401, still hardened
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
            .andExpect(header().string("Permissions-Policy", org.hamcrest.Matchers.containsString("camera=()")))
            .andExpect(header().doesNotExist("Strict-Transport-Security")); // plain http: no HSTS
    }

    @Test
    void hstsOnlyOverHttps() throws Exception {
        mvc.perform(get("/api/v1/threats").secure(true))
            .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    void corsPreflight_namedOriginAllowed_anyOtherRefused() throws Exception {
        mvc.perform(options("/api/v1/threats").header("Origin", "https://admin.fraudshield.example")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://admin.fraudshield.example"));
        mvc.perform(options("/api/v1/threats").header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void appRequestsWithoutAnOriginAreUnaffected() throws Exception {
        mvc.perform(get("/api/v1/threats")).andExpect(status().isUnauthorized()); // normal auth result, not a CORS 403
    }
}
