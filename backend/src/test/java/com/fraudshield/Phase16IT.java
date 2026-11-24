package com.fraudshield;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.calls.NumberReputationService;
import com.fraudshield.calls.PhoneNumbers;
import com.fraudshield.config.AdminBootstrap;
import com.fraudshield.service.BlocklistSyncService;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Phase 16 call guard backend (16.1-16.5, 16.14) against real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {"fraudshield.admin-email=callboss@t.com", "logging.level.com.fraudshield=DEBUG",
        "fraudshield.calls.daily-report-limit=25", "fraudshield.calls.risky-country-codes=+92,+234"})
class Phase16IT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired AdminBootstrap bootstrap;
    @Autowired BlocklistSyncService sync;
    @Autowired JdbcTemplate db;
    static final ObjectMapper JSON = new ObjectMapper();

    private String login(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"));
        String b = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + b.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    /** An account that is 30 days old: a trusted reporter. */
    private String trusted(String email) throws Exception {
        String t = login(email);
        db.update("update users set created_at = now() - interval '30 days' where email = ?", email);
        return t;
    }

    private ResultActions report(String token, String number, String category) throws Exception {
        return mvc.perform(post("/api/v1/numbers/report").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("number", number, "category", category, "callerName", "Ramesh Kumar Sharma"))));
    }

    private JsonNode body(ResultActions r) throws Exception { return JSON.readTree(r.andReturn().getResponse().getContentAsString()); }

    private JsonNode get(String path, String token) throws Exception {
        return body(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Authorization", token))
                .andExpect(status().isOk()));
    }

    private boolean verifies(JsonNode d, String categoryOverride) throws Exception {
        List<NumberReputationService.Entry> added = new ArrayList<>();
        d.get("added").forEach(e -> added.add(new NumberReputationService.Entry(e.get("hash").asText(),
                categoryOverride != null ? categoryOverride : e.get("category").asText())));
        List<String> removed = new ArrayList<>();
        d.get("removed").forEach(x -> removed.add(x.asText()));
        var key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(sync.publicKeyBase64())));
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initVerify(key);
        s.update(NumberReputationService.canonical(d.get("version").asLong(), d.get("full").asBoolean(), added, removed).getBytes(StandardCharsets.UTF_8));
        return s.verify(Base64.getDecoder().decode(d.get("signature").asText()));
    }

    @Test
    void call_guard_end_to_end(CapturedOutput out) throws Exception {
        String scam = "+919812345678";
        String scamHash = PhoneNumbers.hash(scam);

        // ---- 16.5 abuse: one trusted user reporting 20 times is ONE vote
        String loner = trusted("loner@t.com");
        String abused = "+917012345678";
        for (int i = 0; i < 20; i++) {
            JsonNode r = body(report(loner, abused, "SCAM").andExpect(status().isOk()));
            assertEquals("REPORTED", r.get("status").asText());
            assertEquals(i == 0, r.get("counted").asBoolean());
            assertEquals(1, r.get("reports").asInt());
        }
        // ---- five brand-new accounts (sybils) are not enough either: 5 x 0.2
        String sybilTarget = "+918012345678";
        for (int i = 0; i < 5; i++) assertEquals("REPORTED", body(report(login("new" + i + "@t.com"), sybilTarget, "SPAM")).get("status").asText());
        assertEquals(1.0, db.queryForObject("select trust_score from number_reputation where number_hash = ?", Double.class, PhoneNumbers.hash(sybilTarget)));

        // ---- five trusted users, each writing the number differently -> SUSPICIOUS on the fifth
        String[] spellings = {"9812345678", "+91 98123 45678", "098123-45678", "0091 9812345678", "919812345678"};
        for (int i = 0; i < 5; i++) {
            JsonNode r = body(report(trusted("t" + i + "@t.com"), spellings[i], i == 4 ? "SPAM" : "SCAM").andExpect(status().isOk()));
            assertEquals(i == 4 ? "SUSPICIOUS" : "REPORTED", r.get("status").asText(), "after " + (i + 1));
            assertEquals(i + 1, r.get("reports").asInt());
        }
        // a sixth report from the same trusted user changes nothing
        assertFalse(body(report(trusted("t0@t.com"), scam, "SCAM")).get("counted").asBoolean());

        // ---- 16.2 range lookup: only the prefix goes up; the suffix comes back, nothing for non-suspicious numbers
        String any = login("looker@t.com");
        JsonNode range = get("/api/v1/numbers/range/" + scamHash.substring(0, 5).toUpperCase(), any);
        assertEquals(scamHash.substring(0, 5), range.get("prefix").asText());
        JsonNode e = null;
        for (JsonNode x : range.get("entries")) if (x.get("suffix").asText().equals(scamHash.substring(5))) e = x;
        assertNotNull(e, range.toString());
        assertEquals("SCAM", e.get("category").asText());
        assertEquals(5, e.get("reports").asInt());
        assertEquals(5.0, e.get("trust").asDouble());
        assertEquals("SUSPICIOUS", e.get("status").asText());
        String abusedHash = PhoneNumbers.hash(abused);
        for (JsonNode x : get("/api/v1/numbers/range/" + abusedHash.substring(0, 5), any).get("entries"))
            assertNotEquals(abusedHash.substring(5), x.get("suffix").asText(), "one user cannot get a number listed");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/numbers/range/xyz12").header("Authorization", any))
                .andExpect(status().isBadRequest());

        // ---- 16.4 signed delta
        JsonNode full = get("/api/v1/config/numbers?since=0", any);
        assertTrue(full.get("full").asBoolean());
        assertEquals(1, full.get("added").size(), "only SUSPICIOUS numbers are listed");
        assertEquals(scamHash, full.get("added").get(0).get("hash").asText());
        assertEquals("SCAM", full.get("added").get(0).get("category").asText());
        assertTrue(verifies(full, null), "phone accepts the signed list");
        assertFalse(verifies(full, "SPAM"), "a tampered list is rejected");
        long v = full.get("version").asLong();
        JsonNode none = get("/api/v1/config/numbers?since=" + v, any);
        assertFalse(none.get("full").asBoolean());
        assertEquals(0, none.get("added").size());
        assertTrue(verifies(none, null));

        // admin un-flags it -> the next delta removes it
        login("callboss@t.com"); bootstrap.promote();
        String admin = login("callboss@t.com");
        mvc.perform(post("/api/v1/admin/numbers/" + scamHash + "/clear").header("Authorization", any)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/numbers/" + scamHash + "/clear").header("Authorization", admin)).andExpect(status().isOk());
        JsonNode removed = get("/api/v1/config/numbers?since=" + v, any);
        assertEquals(List.of(scamHash), List.of(removed.get("removed").get(0).asText()));
        assertTrue(verifies(removed, null));
        assertEquals(0, get("/api/v1/config/numbers?since=0", any).get("added").size());

        // ---- 16.3 rules
        JsonNode rules = get("/api/v1/config/number-rules", any);
        assertEquals("IN_1600_SERVICE", rules.get("rules").get(0).get("id").asText());
        assertEquals("NEVER_BLOCK", rules.get("rules").get(0).get("action").asText());
        assertEquals("[\"+234\",\"+92\"]", rules.get("riskyCountryCodes").toString());
        assertTrue(rules.get("sources").get("TRAI-DIR-2025-11-19").get("url").asText().startsWith("https://www.trai.gov.in/"));

        // ---- 16.5 input checks, TRAI-protected series, daily limit, login needed
        report(any, "1600-300-123", "SPAM").andExpect(status().isUnprocessableEntity());
        report(any, "+91 140 123 4567", "TELEMARKETING").andExpect(status().isUnprocessableEntity());
        report(any, "1930", "SCAM").andExpect(status().isBadRequest());
        report(any, "+919876500000", "LOVELY").andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/numbers/report").contentType(MediaType.APPLICATION_JSON).content("{\"number\":\"9876500000\",\"category\":\"SCAM\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/config/number-rules")).andExpect(status().isUnauthorized());
        String busy = trusted("busy@t.com");
        for (int i = 0; i < 25; i++) report(busy, "+9190000000" + String.format("%02d", i), "SPAM").andExpect(status().isOk());
        report(busy, "+919000000099", "SPAM").andExpect(status().isTooManyRequests());
        report(busy, "+919000000001", "SCAM").andExpect(status().isOk()); // changing an existing vote is still allowed

        // ---- 16.14 privacy: no full number and no caller identity in the logs or in any DB text column
        List<String> secrets = new ArrayList<>(List.of("9812345678", "98123 45678", "98123-45678", "7012345678", "8012345678",
                "9000000099", "1600300123", "1600-300-123", "Ramesh", "Kumar Sharma"));
        for (String s : secrets) assertFalse(out.getAll().contains(s), "log contains " + s);
        List<Map<String, Object>> cols = db.queryForList("select table_name, column_name from information_schema.columns "
                + "where table_schema = 'public' and data_type in ('text', 'character varying')");
        assertFalse(cols.isEmpty());
        for (var c : cols) {
            String sql = "select count(*) from \"" + c.get("table_name") + "\" where \"" + c.get("column_name") + "\" like ?";
            for (String s : secrets)
                assertEquals(0, db.queryForObject(sql, Long.class, "%" + s + "%"), c + " contains " + s);
        }
        // the call-guard tables have no column that could hold a number or a name
        assertEquals(List.of("category", "created_at", "id", "number_hash", "user_id", "weight"),
                db.queryForList("select column_name from information_schema.columns where table_name = 'number_reports' order by 1", String.class));
        assertEquals(List.of("category", "number_hash", "report_count", "suspicious", "trust_score", "updated_at"),
                db.queryForList("select column_name from information_schema.columns where table_name = 'number_reputation' order by 1", String.class));
        assertEquals(List.of("category", "created_at", "number_hash", "op", "version"),
                db.queryForList("select column_name from information_schema.columns where table_name = 'number_changes' order by 1", String.class));
    }
}
