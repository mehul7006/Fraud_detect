package com.fraudshield;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 9.2 audit: DELETE /me removes ALL of the user's rows. The tables are discovered from the live schema, so a table
 * added later is checked too, and every foreign key to users must cascade (a new table that forgets it fails here).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PrivacyDeletionIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    private final ObjectMapper json = new ObjectMapper();

    private ResultActions post_(String path, String t, String body) throws Exception {
        var b = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (t != null) b.header("Authorization", "Bearer " + t);
        return mvc.perform(b);
    }

    /** every table that has a user_id column, read from the live schema */
    private List<String> userTables() {
        return db.queryForList("select table_name from information_schema.columns where table_schema = 'public' "
                + "and column_name = 'user_id' order by table_name", String.class);
    }

    private long rows(String table, long uid) {
        return db.queryForObject("select count(*) from " + table + " where user_id = ?", Long.class, uid);
    }

    @Test
    void everyForeignKeyToUsersCascades() {
        List<String> notCascading = db.queryForList(
                "select tc.table_name from information_schema.table_constraints tc "
                + "join information_schema.referential_constraints rc on rc.constraint_name = tc.constraint_name "
                + "join information_schema.constraint_column_usage cu on cu.constraint_name = tc.constraint_name "
                + "where tc.constraint_type = 'FOREIGN KEY' and cu.table_name = 'users' and rc.delete_rule <> 'CASCADE'",
                String.class);
        assertEquals(List.of(), notCascading, "these tables would keep a deleted user's data");
    }

    @Test
    void deleteMe_leavesNoRowInAnyUserTable() throws Exception {
        String email = UUID.randomUUID() + "@t.com";
        var reg = json.readTree(post_("/api/v1/auth/register", null, "{\"email\":\"" + email + "\",\"password\":\"password123\"}")
                .andReturn().getResponse().getContentAsString());
        String t = reg.get("token").asText();
        long uid = db.queryForObject("select id from users where email = ?", Long.class, email);

        // put the user's data into every kind of table
        post_("/api/v1/devices", t, "{\"deviceId\":\"d1\",\"fingerprint\":\"f\",\"simHash\":\"s\",\"playProtectOn\":true}").andExpect(status().isOk());
        post_("/api/v1/threats", t, "{\"type\":\"UNEXPECTED_OTP\",\"severity\":\"HIGH\",\"title\":\"otp\"}").andExpect(status().isCreated());
        post_("/api/v1/scan/url", t, "{\"url\":\"http://192.168.1.9/kyc-update/verify.apk\",\"sourceApp\":\"sms\"}").andExpect(status().isOk());
        post_("/api/v1/scan/url/feedback", t, "{\"url\":\"http://example.org/x\",\"kind\":\"FALSE_POSITIVE\",\"note\":\"mine\"}");
        String app = "{\"packageName\":\"com.spam.cleaner\",\"label\":\"x\",\"notifPerHour\":30,\"hasOverlayPermission\":true,\"hasFullScreenIntent\":true}";
        post_("/api/v1/scan/apps", t, "{\"deviceId\":\"d1\",\"apps\":[" + app + "]}").andExpect(status().isOk());
        post_("/api/v1/scan/apps/label", t, "{\"app\":" + app + ",\"label\":\"ABUSIVE\",\"scoreShown\":80}");
        post_("/api/v1/auth/background-session", t, "{}").andExpect(status().isOk());

        List<String> tables = userTables();
        assertTrue(tables.containsAll(List.of("devices", "threat_events", "device_apps", "app_labels", "refresh_tokens", "url_feedback")),
                "schema tables with user_id: " + tables);
        long before = tables.stream().mapToLong(tb -> rows(tb, uid)).sum();
        assertTrue(before >= 8, "test data must really be there first, got " + before);

        mvc.perform(delete("/api/v1/me").header("Authorization", "Bearer " + t)).andExpect(status().isNoContent());

        for (String tb : tables) assertEquals(0, rows(tb, uid), "rows left in " + tb);
        assertEquals(0, db.queryForObject("select count(*) from users where id = ?", Long.class, uid));
        assertEquals(0, db.queryForObject("select count(*) from users where email = ?", Long.class, email));
    }
}
