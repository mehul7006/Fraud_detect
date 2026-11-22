package com.fraudshield;

import com.fraudshield.service.CarrierClassifier;
import com.fraudshield.service.CarrierClassifier.State;
import com.fraudshield.service.CarrierConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Roadmap 3.1 (de-duplicated forwarding threats) and 3.2 (server-served carrier patterns). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ForwardingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired CarrierConfigService carrierConfig;

    // ---------- helpers ----------
    private String token() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + UUID.randomUUID() + "@t.com\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private ResultActions report(String t, String type) throws Exception {
        return mvc.perform(post("/api/v1/threats").header("Authorization", "Bearer " + t)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\",\"severity\":\"CRITICAL\",\"title\":\"" + type + " title\"}"));
    }

    private ResultActions list(String t, String status) throws Exception {
        return mvc.perform(get("/api/v1/threats?status=" + status).header("Authorization", "Bearer " + t));
    }

    // ---------- 3.1 de-duplication ----------
    @Test
    void secondForwardingReport_returns200WithSameThreat_noDuplicateRow() throws Exception {
        String t = token();
        String first = report(t, "CALL_FORWARDING").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = first.replaceAll(".*\"id\":(\\d+).*", "$1");

        report(t, "CALL_FORWARDING").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(Integer.parseInt(id)));
        report(t, "CALL_FORWARDING").andExpect(status().isOk());
        list(t, "OPEN").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void afterResolving_aNewForwardingReportCreatesANewThreat() throws Exception {
        String t = token();
        String id = report(t, "CALL_FORWARDING").andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"id\":(\\d+).*", "$1");
        mvc.perform(post("/api/v1/threats/" + id + "/resolve").header("Authorization", "Bearer " + t))
                .andExpect(status().isNoContent());
        report(t, "CALL_FORWARDING").andExpect(status().isCreated());
        list(t, "OPEN").andExpect(jsonPath("$", hasSize(1)));
        list(t, "RESOLVED").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void differentTypesAndDifferentUsers_doNotBlockEachOther() throws Exception {
        String a = token(), b = token();
        report(a, "CALL_FORWARDING").andExpect(status().isCreated());
        report(a, "SMS_FORWARDING").andExpect(status().isCreated());
        report(a, "WHATSAPP_TAKEOVER").andExpect(status().isCreated());
        report(a, "TELEGRAM_TAKEOVER").andExpect(status().isCreated());
        report(b, "CALL_FORWARDING").andExpect(status().isCreated());
        list(a, "OPEN").andExpect(jsonPath("$", hasSize(4)));
        list(b, "OPEN").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void perEventThreats_likeUnexpectedOtp_areNotDeduplicated() throws Exception {
        String t = token();
        report(t, "UNEXPECTED_OTP").andExpect(status().isCreated());
        report(t, "UNEXPECTED_OTP").andExpect(status().isCreated());
        list(t, "OPEN").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void eightSimultaneousReports_createExactlyOneOpenThreat() throws Exception {
        String t = token();
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return report(t, "CALL_FORWARDING").andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        int created = 0, existing = 0;
        for (Future<Integer> f : results) {
            int s = f.get(30, TimeUnit.SECONDS);
            if (s == 201) created++; else if (s == 200) existing++; else fail("unexpected status " + s);
        }
        pool.shutdown();
        assertEquals(1, created, "exactly one request may create the threat");
        assertEquals(n - 1, existing);
        list(t, "OPEN").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void cloneRiskHeartbeats_stillDeduplicate() throws Exception {
        String t = token();
        String dev = "{\"deviceId\":\"d1\",\"fingerprint\":\"f1\",\"rooted\":true,\"playProtectOn\":false,"
                + "\"remoteAppsInstalled\":[\"com.anydesk.anydeskandroid\"]}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/devices/heartbeat").header("Authorization", "Bearer " + t)
                    .contentType(MediaType.APPLICATION_JSON).content(dev)).andExpect(status().isOk());
        }
        list(t, "OPEN").andExpect(jsonPath("$", hasSize(1)));
    }

    // ---------- 3.2 carrier config ----------
    @Test
    void carrierConfig_requiresLogin_andReturnsRulesWithVersion() throws Exception {
        mvc.perform(get("/api/v1/config/carriers")).andExpect(status().isUnauthorized());
        String t = token();
        mvc.perform(get("/api/v1/config/carriers").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.version", matchesPattern("[0-9a-f]{16}")))
                .andExpect(jsonPath("$.rules", hasSize(greaterThanOrEqualTo(6))))
                .andExpect(jsonPath("$.rules[0].priority").value(10))        // evaluation order: lowest first
                .andExpect(jsonPath("$.rules[0].kind").value("INACTIVE"));
    }

    @Test
    void carrierConfig_supportsEtag_304WhenUnchanged() throws Exception {
        String t = token();
        String etag = mvc.perform(get("/api/v1/config/carriers").header("Authorization", "Bearer " + t))
                .andReturn().getResponse().getHeader("ETag");
        assertNotNull(etag);
        mvc.perform(get("/api/v1/config/carriers").header("Authorization", "Bearer " + t).header("If-None-Match", etag))
                .andExpect(status().isNotModified());
        mvc.perform(get("/api/v1/config/carriers").header("Authorization", "Bearer " + t).header("If-None-Match", "\"stale\""))
                .andExpect(status().isOk());
    }

    @Test
    void everySeedRule_compilesAndIsWithinLimits() {
        for (var r : carrierConfig.config().rules()) {
            assertDoesNotThrow(() -> Pattern.compile(r.regex()), "bad regex: " + r.regex());
            assertTrue(r.regex().length() <= 200, "regex too long: " + r.regex());
            assertTrue(r.kind().equals("ACTIVE") || r.kind().equals("INACTIVE"));
        }
    }

    /**
     * 3.4: the seed rules in the database (V2 + V5) must classify every row of the corpus shared with the app,
     * mobile/test/fixtures/ussd_responses.json, exactly like the app's built-in rules do.
     */
    @Test
    void seedRules_classifyTheSharedCorpus() throws Exception {
        CarrierClassifier c = carrierConfig.classifier();
        java.nio.file.Path f = java.nio.file.Path.of("..", "mobile", "test", "fixtures", "ussd_responses.json");
        assertTrue(java.nio.file.Files.exists(f), "shared corpus not found at " + f.toAbsolutePath());
        var rows = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(java.nio.file.Files.readString(f, java.nio.charset.StandardCharsets.UTF_8)).get("rows");
        int n = 0;
        for (var r : rows) {
            String text = r.get("text").asText();
            assertEquals(State.valueOf(r.get("expected").asText()), c.classify(text),
                    "[" + r.get("source").asText() + "] text: \"" + text + "\"");
            n++;
        }
        assertTrue(n >= 20, "corpus too small: " + n);
    }
}
