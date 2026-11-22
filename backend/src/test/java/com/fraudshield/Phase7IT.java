package com.fraudshield;

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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Roadmap 7.1 (multi-device clone) and 7.2 (takeover correlation) against real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Phase7IT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");

    private ResultActions postJson(String path, String token, String json) throws Exception {
        var b = post(path).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mvc.perform(b);
    }

    private String user() throws Exception {
        String body = postJson("/api/v1/auth/register", null,
                "{\"email\":\"" + UUID.randomUUID() + "@t.com\",\"password\":\"password123\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher m = TOKEN.matcher(body);
        if (!m.find()) throw new AssertionError(body);
        return m.group(1);
    }

    private static String device(String id, String fingerprint, String sim, String model) {
        return "{\"deviceId\":\"" + id + "\",\"model\":\"" + model + "\",\"fingerprint\":\"" + fingerprint
                + "\",\"simHash\":\"" + sim + "\",\"playProtectOn\":true}";
    }

    private ResultActions report(String t, String type, String title) throws Exception {
        return postJson("/api/v1/threats", t, "{\"type\":\"" + type + "\",\"severity\":\"HIGH\",\"title\":\"" + title
                + "\",\"detail\":\"\",\"sourceApp\":\"test\"}");
    }

    private ResultActions takeovers(String t) throws Exception {
        return mvc.perform(get("/api/v1/threats").header("Authorization", "Bearer " + t));
    }

    // ---------- 7.1 ----------
    @Test
    void secondDeviceWithIdenticalFingerprintOnSameAccount_adds50AndRaisesCloneRisk() throws Exception {
        String t = user();
        postJson("/api/v1/devices", t, device("phone-a", "same-fp", "sim1", "Pixel 7"))
                .andExpect(jsonPath("$.cloneRiskScore").value(0));
        postJson("/api/v1/devices", t, device("phone-b", "same-fp", "sim2", "Pixel 7"))
                .andExpect(jsonPath("$.cloneRiskScore").value(50))
                .andExpect(jsonPath("$.reasons", hasItem(containsString("identical fingerprint"))));
        takeovers(t).andExpect(jsonPath("$[?(@.type=='CLONE_RISK')]", hasSize(1)));
    }

    @Test
    void secondDeviceWithDifferentFingerprint_isNotAClone() throws Exception {
        String t = user();
        postJson("/api/v1/devices", t, device("phone-a", "fp-a", "sim1", "Pixel 7"));
        postJson("/api/v1/devices", t, device("phone-b", "fp-b", "sim2", "Galaxy S23"))
                .andExpect(jsonPath("$.cloneRiskScore").value(0));
    }

    // ---------- 7.2 ----------
    @Test
    void loginCodeAlone_onTheOnlyPhone_isNotATakeover() throws Exception {
        String t = user();
        postJson("/api/v1/devices", t, device("phone-a", "fp-a", "sim1", "Pixel 7"));
        report(t, "UNEXPECTED_OTP", "Unexpected OTP from HDFC").andExpect(status().isCreated());
        takeovers(t).andExpect(jsonPath("$[?(@.type=='ACCOUNT_TAKEOVER')]", hasSize(0)));
    }

    @Test
    void newDeviceThenLoginCode_withinMinutes_raisesOneCriticalTakeover() throws Exception {
        String t = user();
        postJson("/api/v1/devices", t, device("phone-a", "fp-a", "sim1", "Pixel 7"));
        postJson("/api/v1/devices", t, device("attacker", "fp-x", "sim9", "Redmi 12"));
        takeovers(t).andExpect(jsonPath("$[?(@.type=='ACCOUNT_TAKEOVER')]", hasSize(0)));   // new device alone: no
        report(t, "TELEGRAM_TAKEOVER", "Telegram login code").andExpect(status().isCreated());
        takeovers(t)
            .andExpect(jsonPath("$[?(@.type=='ACCOUNT_TAKEOVER')]", hasSize(1)))
            .andExpect(jsonPath("$[?(@.type=='ACCOUNT_TAKEOVER')].severity", contains("CRITICAL")))
            .andExpect(jsonPath("$[?(@.type=='ACCOUNT_TAKEOVER')].detail",
                    contains(allOf(containsString("New device signed in: Redmi 12"), containsString("Telegram login code")))));
        report(t, "UNEXPECTED_OTP", "Another OTP");  // more signals: still ONE open takeover
        takeovers(t).andExpect(jsonPath("$[?(@.type=='ACCOUNT_TAKEOVER')]", hasSize(1)));
    }

    @Test
    void loginCodeThenSimSwap_isATakeoverToo() throws Exception {
        String t = user();
        postJson("/api/v1/devices", t, device("phone-a", "fp-a", "sim1", "Pixel 7"));
        report(t, "WHATSAPP_TAKEOVER", "WhatsApp registration code");
        takeovers(t).andExpect(jsonPath("$[?(@.type=='ACCOUNT_TAKEOVER')]", hasSize(0)));
        postJson("/api/v1/devices/heartbeat", t, device("phone-a", "fp-a", "sim-SWAPPED", "Pixel 7"));
        takeovers(t)
            .andExpect(jsonPath("$[?(@.type=='ACCOUNT_TAKEOVER')]", hasSize(1)))
            .andExpect(jsonPath("$[?(@.type=='ACCOUNT_TAKEOVER')].detail", contains(containsString("SIM card changed"))));
    }

    @Test
    void unrelatedThreatPlusNewDevice_isNotATakeover() throws Exception {
        String t = user();
        postJson("/api/v1/devices", t, device("phone-a", "fp-a", "sim1", "Pixel 7"));
        postJson("/api/v1/devices", t, device("tablet", "fp-t", "sim2", "Tab S9"));
        report(t, "CALL_FORWARDING", "Call forwarding is ON");
        takeovers(t).andExpect(jsonPath("$[?(@.type=='ACCOUNT_TAKEOVER')]", hasSize(0)));
    }
}
