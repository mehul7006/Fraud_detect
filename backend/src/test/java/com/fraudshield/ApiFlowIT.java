package com.fraudshield;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Covers roadmap 1.2 - 1.8 against a real PostgreSQL (Flyway schema included). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;

    // ---------- helpers ----------
    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");

    private String email() { return UUID.randomUUID() + "@test.com"; }

    private ResultActions postJson(String path, String token, String json) throws Exception {
        MockHttpServletRequestBuilder b = post(path).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mvc.perform(b);
    }

    private String register(String email) throws Exception {
        String body = postJson("/api/v1/auth/register", null, "{\"email\":\"" + email + "\",\"password\":\"password123\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher m = TOKEN.matcher(body);
        if (!m.find()) throw new AssertionError("no token in " + body);
        return m.group(1);
    }

    private String token() throws Exception { return register(email()); }

    private ResultActions getAuth(String path, String token) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    // ---------- 1.2 auth + 1.8 error shape ----------
    @Test
    void duplicateRegister_is409_withStandardErrorShape() throws Exception {
        String e = email();
        register(e);
        postJson("/api/v1/auth/register", null, "{\"email\":\"" + e + "\",\"password\":\"password123\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email already used"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void wrongPassword_is401() throws Exception {
        String e = email();
        register(e);
        postJson("/api/v1/auth/login", null, "{\"email\":\"" + e + "\",\"password\":\"wrongpass1\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void emptyBody_is400_namingTheField() throws Exception {
        postJson("/api/v1/auth/login", null, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("email")));
    }

    @Test
    void shortPassword_is400() throws Exception {
        postJson("/api/v1/auth/register", null, "{\"email\":\"" + email() + "\",\"password\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("password")));
    }

    @Test
    void malformedJson_is400_withoutLeakingParserDetails() throws Exception {
        postJson("/api/v1/auth/login", null, "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));
    }

    @Test
    void noToken_is401_json() throws Exception {
        mvc.perform(get("/api/v1/threats"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/api/v1/threats"));
    }

    @Test
    void garbageToken_is401() throws Exception {
        getAuth("/api/v1/threats", "not.a.jwt").andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPath_is404_andWrongMethod_is405() throws Exception {
        String t = token();
        getAuth("/api/v1/nope", t).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(put("/api/v1/threats").header("Authorization", "Bearer " + t))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void requestIdHeader_isEchoed_andBadOnesReplaced() throws Exception {
        mvc.perform(get("/api/v1/threats").header("X-Request-Id", "abc-12345-xyz"))
                .andExpect(header().string("X-Request-Id", "abc-12345-xyz"));
        mvc.perform(get("/api/v1/threats").header("X-Request-Id", "bad id with spaces!"))
                .andExpect(header().string("X-Request-Id", not("bad id with spaces!")));
    }

    // ---------- 1.4 url scan ----------
    @Test
    void urlScan_flagsPhishing_safeForGoogle_andRecordsThreat() throws Exception {
        String t = token();
        postJson("/api/v1/scan/url", t, "{\"url\":\"http://whatsapp.verify-login-now.xyz/update\",\"sourceApp\":\"com.whatsapp\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$.score", greaterThanOrEqualTo(70)));
        postJson("/api/v1/scan/url", t, "{\"url\":\"https://www.google.com\"}")
                .andExpect(jsonPath("$.verdict").value("SAFE"));
        postJson("/api/v1/scan/url", t, "{\"url\":\"http://example-phish.test/x\"}")
                .andExpect(jsonPath("$.score").value(100));
        getAuth("/api/v1/threats?status=OPEN", t).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("MALICIOUS_LINK"));
    }

    @Test
    void urlScan_blankUrl_is400() throws Exception {
        postJson("/api/v1/scan/url", token(), "{\"url\":\"\"}").andExpect(status().isBadRequest());
    }

    // ---------- 1.5 file scan ----------
    @Test
    void fileScan_doubleExtensionApk_andKnownHash() throws Exception {
        String t = token();
        postJson("/api/v1/scan/file", t, "{\"sha256\":\"" + "b".repeat(64) + "\",\"fileName\":\"invoice.pdf.apk\",\"sizeBytes\":900000,"
                + "\"sourceApp\":\"com.whatsapp\",\"headerHex\":\"504b0304\"}")
                .andExpect(jsonPath("$.verdict").value("MALICIOUS")).andExpect(jsonPath("$.action").value("DELETE"));
        postJson("/api/v1/scan/file", t, "{\"sha256\":\"275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f\",\"fileName\":\"a.txt\",\"sizeBytes\":68}")
                .andExpect(jsonPath("$.reasons[0]").value("Known malicious file hash"));
        postJson("/api/v1/scan/file", t, "{\"sha256\":\"" + "c".repeat(64) + "\",\"fileName\":\"holiday.jpg\",\"sizeBytes\":500000,\"headerHex\":\"ffd8ffe0\"}")
                .andExpect(jsonPath("$.verdict").value("SAFE")).andExpect(jsonPath("$.action").value("NONE"));
    }

    @Test
    void fileScan_badHashLength_is400() throws Exception {
        postJson("/api/v1/scan/file", token(), "{\"sha256\":\"abc\",\"fileName\":\"a.apk\",\"sizeBytes\":1}").andExpect(status().isBadRequest());
    }

    // ---------- 1.6 ad abuse ----------
    @Test
    void appsScan_scoresSpamAppHigh_andSystemAppSafe() throws Exception {
        String t = token();
        postJson("/api/v1/scan/apps", t, "{\"apps\":["
                + "{\"packageName\":\"com.spam\",\"label\":\"Spam\",\"notifPerHour\":30,\"hasOverlayPermission\":true,\"hasFullScreenIntent\":true},"
                + "{\"packageName\":\"com.android.settings\",\"label\":\"Settings\",\"notifPerHour\":50,\"hasOverlayPermission\":true,\"isSystem\":true}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$.results[1].adAbuseScore").value(0));
        getAuth("/api/v1/threats", t).andExpect(jsonPath("$[0].type").value("AD_ABUSE")).andExpect(jsonPath("$", hasSize(1)));
    }

    // ---------- 1.3 clone risk ----------
    @Test
    void heartbeat_scoresRisk_simChangeRaisesIt_andOnlyOneOpenThreat() throws Exception {
        String t = token();
        String dev = "{\"deviceId\":\"d1\",\"fingerprint\":\"f1\",\"simHash\":\"%s\",\"rooted\":true,\"playProtectOn\":false,"
                + "\"remoteAppsInstalled\":[\"com.anydesk.anydeskandroid\"]}";
        postJson("/api/v1/devices/heartbeat", t, dev.formatted("s1")).andExpect(jsonPath("$.cloneRiskScore").value(55));
        postJson("/api/v1/devices/heartbeat", t, dev.formatted("s2")).andExpect(jsonPath("$.cloneRiskScore").value(85))
                .andExpect(jsonPath("$.reasons", hasItem("SIM card changed since last check")));
        postJson("/api/v1/devices/heartbeat", t, dev.formatted("s2"));
        getAuth("/api/v1/threats?status=OPEN", t).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("CLONE_RISK"));
    }

    @Test
    void heartbeat_cleanDevice_hasNoThreat() throws Exception {
        String t = token();
        postJson("/api/v1/devices", t, "{\"deviceId\":\"clean\",\"fingerprint\":\"fx\",\"simHash\":\"s\",\"playProtectOn\":true}")
                .andExpect(jsonPath("$.cloneRiskScore").value(0));
        getAuth("/api/v1/threats", t).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- 1.7 threat lifecycle + isolation + delete ----------
    @Test
    void threatLifecycle_reportListResolve_andUsersAreIsolated() throws Exception {
        String a = token(), b = token();
        postJson("/api/v1/threats", a, "{\"type\":\"CALL_FORWARDING\",\"severity\":\"CRITICAL\",\"title\":\"Call forwarding is ON\"}")
                .andExpect(status().isCreated());
        String body = getAuth("/api/v1/threats?status=OPEN", a).andExpect(jsonPath("$", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":(\\d+).*", "$1");

        getAuth("/api/v1/threats", b).andExpect(jsonPath("$", hasSize(0)));            // other user sees nothing
        postJson("/api/v1/threats/" + id + "/resolve", b, "").andExpect(status().isNotFound()); // cannot resolve someone else's
        postJson("/api/v1/threats/" + id + "/resolve", a, "").andExpect(status().isNoContent());
        getAuth("/api/v1/threats?status=OPEN", a).andExpect(jsonPath("$", hasSize(0)));
        getAuth("/api/v1/threats?status=RESOLVED", a).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void report_unknownType_is400() throws Exception {
        postJson("/api/v1/threats", token(), "{\"type\":\"NOPE\",\"severity\":\"CRITICAL\",\"title\":\"x\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Unknown type or severity"));
    }

    @Test
    void deleteMe_removesAccountAndData() throws Exception {
        String e = email();
        String t = register(e);
        postJson("/api/v1/threats", t, "{\"type\":\"CALL_FORWARDING\",\"severity\":\"HIGH\",\"title\":\"x\"}");
        postJson("/api/v1/devices", t, "{\"deviceId\":\"d\",\"playProtectOn\":true}");
        mvc.perform(delete("/api/v1/me").header("Authorization", "Bearer " + t)).andExpect(status().isNoContent());
        postJson("/api/v1/auth/login", null, "{\"email\":\"" + e + "\",\"password\":\"password123\"}").andExpect(status().isUnauthorized());
        // the old, still validly signed token must not work any more: 401 (log in again), never a 500 from the DB
        postJson("/api/v1/scan/url", t, "{\"url\":\"http://example.com\"}").andExpect(status().isUnauthorized());
        postJson("/api/v1/threats", t, "{\"type\":\"CALL_FORWARDING\",\"severity\":\"HIGH\",\"title\":\"x\"}")
            .andExpect(status().isUnauthorized());
    }
}
