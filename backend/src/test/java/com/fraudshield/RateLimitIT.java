package com.fraudshield;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 8.1: per-user / per-IP limits. Small limits here so the test is quick (production default: 100/min, auth 10/min). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {"fraudshield.ratelimit.per-minute=5", "fraudshield.ratelimit.auth-per-minute=3"})
class RateLimitIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");

    private ResultActions register(String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + UUID.randomUUID() + "@t.com\",\"password\":\"password123\"}"));
    }

    private String token(ResultActions r) throws Exception {
        Matcher m = TOKEN.matcher(r.andReturn().getResponse().getContentAsString());
        if (!m.find()) throw new AssertionError("no token");
        return m.group(1);
    }

    private ResultActions threats(String t) throws Exception {
        return mvc.perform(get("/api/v1/threats").header("Authorization", "Bearer " + t));
    }

    @Test
    void loginAndRegister_areLimitedPerIp_with429RetryAfterAndStandardErrorShape() throws Exception {
        for (int i = 0; i < 3; i++) register("10.0.0.1").andExpect(status().isOk());
        register("10.0.0.1")
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Too many login attempts")))
            .andExpect(jsonPath("$.requestId").isNotEmpty());
        register("10.0.0.2").andExpect(status().isOk()); // another address is not affected
    }

    @Test
    void apiCalls_areLimitedPerUser_notPerIp() throws Exception {
        String a = token(register("10.0.1.1")), b = token(register("10.0.1.2"));
        for (int i = 0; i < 5; i++) threats(a).andExpect(status().isOk()).andExpect(header().exists("X-RateLimit-Remaining"));
        threats(a).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        threats(b).andExpect(status().isOk()); // the other user still has their own budget
    }

    @Test
    void healthCheckIsNeverLimited() throws Exception {
        for (int i = 0; i < 20; i++) mvc.perform(get("/actuator/health")).andExpect(status().is(org.hamcrest.Matchers.not(429)));
    }
}
