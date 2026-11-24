package com.fraudshield;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.config.AdminBootstrap;
import com.fraudshield.safety.CrowdItem;
import com.fraudshield.safety.SafetyValues;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Phase 15 backend: 15.1 live-scam correlation, 15.5 UPI / QR check, 15.7 crowd reports, 15.9 customer-care numbers. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {"fraudshield.admin-email=safetyboss@t.com", "logging.level.com.fraudshield=DEBUG"})
class Phase15IT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminBootstrap bootstrap;
    static final ObjectMapper JSON = new ObjectMapper();

    private String login(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"));
        String b = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + b.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private String newUser() throws Exception { return login(UUID.randomUUID() + "@t.com"); }

    /** an established account: registered a month ago */
    private String trustedUser() throws Exception {
        String email = UUID.randomUUID() + "@t.com";
        String t = login(email);
        jdbc.update("update users set created_at = now() - interval '30 days' where email = ?", email);
        return t;
    }

    private JsonNode postJson(String path, String token, String body, int expect) throws Exception {
        String s = mvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expect)).andReturn().getResponse().getContentAsString();
        return s.isEmpty() ? null : JSON.readTree(s);
    }

    private JsonNode getJson(String path, String token) throws Exception {
        return JSON.readTree(mvc.perform(get(path).header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode signal(String t, String body) throws Exception { return postJson("/api/v1/scam-signals", t, body, 200); }

    private JsonNode report(String t, String type, String value) throws Exception {
        return postJson("/api/v1/reports", t, "{\"type\":\"" + type + "\",\"value\":\"" + value + "\"}", 202);
    }

    // ---- 15.1

    @Test
    void fourLiveScamSignals_raiseOneCriticalThreat_twoDoNot() throws Exception {
        String t = newUser();
        assertEquals("NONE", signal(t, "{\"signal\":\"LONG_UNKNOWN_CALL\",\"unknownCaller\":true,\"callSeconds\":240}").get("level").asText());
        signal(t, "{\"signal\":\"REMOTE_ACCESS_APP\"}");
        assertEquals("WARNING", signal(t, "{\"signal\":\"BANKING_APP\"}").get("level").asText());
        JsonNode r = signal(t, "{\"signal\":\"OTP_SEEN\"}");
        assertEquals("CRITICAL", r.get("level").asText());
        long id = r.get("threatId").asLong();
        assertTrue(r.get("advice").get("hi").asText().contains("1930"));
        // the phone keeps sending while the call goes on: still ONE open threat
        assertEquals(id, signal(t, "{\"signal\":\"OTP_SEEN\"}").get("threatId").asLong());
        JsonNode threats = getJson("/api/v1/threats", t);
        int live = 0;
        for (JsonNode th : threats) if (th.get("type").asText().equals("LIVE_SCAM")) {
            live++;
            assertEquals("CRITICAL", th.get("severity").asText());
        }
        assertEquals(1, live);

        String other = newUser();
        signal(other, "{\"signal\":\"BANKING_APP\"}");
        assertEquals("NONE", signal(other, "{\"signal\":\"OTP_SEEN\"}").get("level").asText());
        assertEquals(0, getJson("/api/v1/threats", other).size());
        // a short call does not count
        assertFalse(signal(other, "{\"signal\":\"LONG_UNKNOWN_CALL\",\"unknownCaller\":true,\"callSeconds\":30}").get("counted").asBoolean());

        JsonNode cfg = getJson("/api/v1/scam-signals/config", other);
        assertEquals(10, cfg.get("windowMinutes").asInt());
        assertTrue(cfg.get("remoteAccessPackages").toString().contains("com.anydesk.anydeskandroid"));
        postJson("/api/v1/scam-signals", other, "{\"signal\":\"NOPE\"}", 400);
    }

    // ---- 15.7

    @Test
    void fiveTrustedReports_markSuspicious_thenModeratorConfirmBlocksTheSite() throws Exception {
        String host = "crowd-" + UUID.randomUUID().toString().substring(0, 8) + ".top";
        for (int i = 1; i <= 5; i++) {
            JsonNode r = report(trustedUser(), "LINK", "https://www." + host + "/login?id=" + i);
            assertTrue(r.get("counted").asBoolean());
            assertEquals(i < 5 ? "UNVERIFIED" : "SUSPICIOUS", r.get("status").asText(), "after " + i + " reports");
        }
        String any = newUser();
        JsonNode st = getJson("/api/v1/reports/status?type=LINK&value=" + host, any);
        assertEquals("SUSPICIOUS", st.get("status").asText());
        assertEquals(5, st.get("reportCount").asInt());

        String adm = login("safetyboss@t.com");
        bootstrap.promote();
        adm = login("safetyboss@t.com");
        mvc.perform(get("/api/v1/admin/reports/queue").header("Authorization", any)).andExpect(status().isForbidden());
        JsonNode q = getJson("/api/v1/admin/reports/queue", adm);
        long id = -1;
        for (JsonNode i : q) if (host.equals(i.path("linkHost").asText())) id = i.get("id").asLong();
        assertTrue(id > 0, "item is in the moderation queue");
        JsonNode done = postJson("/api/v1/admin/reports/" + id + "/review", adm, "{\"decision\":\"CONFIRM\"}", 200);
        assertEquals("CONFIRMED", done.get("status").asText());
        assertEquals(1, jdbc.queryForObject("select count(*) from blocklist where kind='DOMAIN' and value=?", Integer.class, host));
        assertEquals("MALICIOUS", postJson("/api/v1/scan/url", any, "{\"url\":\"https://" + host + "/x\"}", 200).get("verdict").asText());
    }

    @Test
    void oneUserTwentyReports_andTwentyNewAccounts_doNotMarkAnything(CapturedOutput out) throws Exception {
        String vpa = "abuse" + UUID.randomUUID().toString().substring(0, 6) + "@ybl";
        String t = trustedUser();
        for (int i = 0; i < 20; i++) {
            JsonNode r = i % 2 == 0 ? report(t, "UPI", vpa)
                    : postJson("/api/v1/reports", t, "{\"type\":\"UPI\",\"valueHash\":\"" + SafetyValues.hash(CrowdItem.Type.UPI, vpa) + "\"}", 202);
            assertEquals(i == 0, r.get("counted").asBoolean(), "only the first report of this user counts (#" + i + ")");
            assertEquals("UNVERIFIED", r.get("status").asText());
        }
        assertEquals(1, getJson("/api/v1/reports/status?type=UPI&value=" + vpa, t).get("reportCount").asInt());

        // 20 brand-new accounts (a bot farm) - each weighs 0.2: 20 x 0.2 = 4 < 5
        String vpa2 = "sybil" + UUID.randomUUID().toString().substring(0, 6) + "@ybl";
        for (int i = 0; i < 20; i++) report(newUser(), "UPI", vpa2);
        JsonNode st = getJson("/api/v1/reports/status?type=UPI&value=" + vpa2, t);
        assertEquals("UNVERIFIED", st.get("status").asText());
        assertEquals(20, st.get("reportCount").asInt());

        // UPI IDs are stored only as hashes: no text column of any table contains them, and no log line does
        for (String v : new String[]{vpa, vpa2}) {
            assertFalse(out.getAll().contains(v), "UPI ID in the log");
            var cols = jdbc.queryForList("select table_name, column_name from information_schema.columns where table_schema='public' and data_type in ('text','character varying')");
            for (var c : cols) {
                Integer n = jdbc.queryForObject("select count(*) from " + c.get("table_name") + " where cast(" + c.get("column_name") + " as text) like ?",
                        Integer.class, "%" + v + "%");
                assertEquals(0, n, v + " found in " + c);
            }
        }
        postJson("/api/v1/reports", t, "{\"type\":\"UPI\"}", 400);
        postJson("/api/v1/reports", t, "{\"type\":\"UPI\",\"valueHash\":\"nothex\"}", 400);
    }

    // ---- 15.5

    @Test
    void reportedVpaAndCollectQrAreFlagged_normalPaymentIsSafe() throws Exception {
        String t = newUser();
        String vpa = "kyc" + UUID.randomUUID().toString().substring(0, 6) + "@ybl";
        assertNotEquals("MALICIOUS", postJson("/api/v1/scan/upi", t, "{\"vpa\":\"" + vpa + "\"}", 200).get("verdict").asText());
        for (int i = 0; i < 5; i++) report(trustedUser(), "UPI", vpa);
        JsonNode r = postJson("/api/v1/scan/upi", t, "{\"vpa\":\"" + vpa.toUpperCase() + "\"}", 200);
        assertEquals("MALICIOUS", r.get("verdict").asText());
        assertTrue(r.get("reasonCodes").toString().contains("COMMUNITY_REPORTED"));
        assertFalse(r.get("reasonsHi").get(0).asText().isBlank());

        JsonNode c = postJson("/api/v1/scan/upi", t,
                "{\"qrPayload\":\"upi://collect?pa=refund.desk@ybl&pn=Refund&am=4999&tn=Receive%20refund\"}", 200);
        assertEquals("COLLECT", c.get("kind").asText());
        assertEquals("MALICIOUS", c.get("verdict").asText());

        JsonNode ok = postJson("/api/v1/scan/upi", t, "{\"qrPayload\":\"upi://pay?pa=sharmagrocery@okaxis&pn=Sharma%20Grocery&am=240&cu=INR\"}", 200);
        assertEquals("SAFE", ok.get("verdict").asText());
        assertEquals("Google Pay", ok.get("payee").get("handleOwner").asText());
        postJson("/api/v1/scan/upi", t, "{}", 400);
        mvc.perform(post("/api/v1/scan/upi").contentType(MediaType.APPLICATION_JSON).content("{\"vpa\":\"a@ybl\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 15.9

    @Test
    void careNumbers_officialIsOfficial_fakeMobileForABrandIsFake_reportedNumberIsFlagged() throws Exception {
        String t = newUser();
        JsonNode cyber = getJson("/api/v1/care-number/1930", t);
        assertTrue(cyber.get("official").asBoolean());
        assertEquals("OFFICIAL", cyber.get("verdict").asText());

        JsonNode fake = getJson("/api/v1/care-number/919876543210?brand=SBI", t);
        assertFalse(fake.get("official").asBoolean());
        assertEquals("FAKE", fake.get("verdict").asText());
        assertEquals("State Bank of India", fake.get("brand").asText());

        // community reports go to the ONE Phase 16 number store (POST /reports type NUMBER/CARE_NUMBER delegates there)
        String num = "040" + (10_000_000 + new java.util.Random().nextInt(89_999_999));
        assertEquals("UNKNOWN", getJson("/api/v1/care-number/" + num, t).get("verdict").asText());
        for (int i = 0; i < 5; i++) {
            JsonNode r = report(trustedUser(), i % 2 == 0 ? "CARE_NUMBER" : "NUMBER", num);
            assertEquals(i < 4 ? "UNVERIFIED" : "SUSPICIOUS", r.get("status").asText());
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from number_reputation where number_hash = ?", Integer.class,
                com.fraudshield.calls.PhoneNumbers.hash(com.fraudshield.calls.PhoneNumbers.canonical(num))));
        JsonNode rep = getJson("/api/v1/care-number/" + num, t);
        assertEquals("FAKE", rep.get("verdict").asText());
        assertTrue(rep.get("reasonCodes").toString().contains("COMMUNITY_REPORTED"));
        mvc.perform(get("/api/v1/care-number/12").header("Authorization", t)).andExpect(status().isBadRequest());
        postJson("/api/v1/reports", t, "{\"type\":\"NUMBER\",\"valueHash\":\"" + "a".repeat(64) + "\"}", 400);
        postJson("/api/v1/reports", t, "{\"type\":\"EMAIL\",\"value\":\"x\"}", 400);
    }
}
