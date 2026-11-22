package com.fraudshield;

import com.fraudshield.config.AdminBootstrap;
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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Roadmap 6.2 (app reputation) and 6.3 (new app since last scan). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "fraudshield.admin-email=boss6@t.com")
class Phase6IT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired AdminBootstrap bootstrap;

    private String login(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"));
        String b = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + b.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private String user() throws Exception { return login(UUID.randomUUID() + "@t.com"); }

    private static String app(String pkg, int notif, boolean overlay, boolean fullScreen) {
        return "{\"packageName\":\"" + pkg + "\",\"label\":\"x\",\"notifPerHour\":" + notif
            + ",\"hasOverlayPermission\":" + overlay + ",\"hasFullScreenIntent\":" + fullScreen + "}";
    }

    private ResultActions scan(String t, String deviceId, String... apps) throws Exception {
        String dev = deviceId == null ? "" : ",\"deviceId\":\"" + deviceId + "\"";
        return mvc.perform(post("/api/v1/scan/apps").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"apps\":[" + String.join(",", apps) + "]" + dev + "}"));
    }

    // ---------- 6.2 ----------
    @Test
    void knownGoodAppIsNeverFlaggedEvenWithManyNotifications() throws Exception {
        scan(user(), null, app("com.whatsapp", 30, true, true))
            .andExpect(jsonPath("$.results[0].verdict").value("SAFE"))
            .andExpect(jsonPath("$.results[0].adAbuseScore").value(0));
    }

    @Test
    void adminMarksPackageBadAndItIsAlwaysMalicious() throws Exception {
        String t = user();
        scan(t, null, app("com.quiet.bad", 0, false, false)).andExpect(jsonPath("$.results[0].verdict").value("SAFE"));
        mvc.perform(post("/api/v1/admin/apps").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"packageName\":\"com.quiet.bad\",\"verdict\":\"BAD\"}")).andExpect(status().isForbidden());
        login("boss6@t.com");
        bootstrap.promote();
        String adm = login("boss6@t.com");
        mvc.perform(post("/api/v1/admin/apps").header("Authorization", adm).contentType(MediaType.APPLICATION_JSON)
            .content("{\"packageName\":\"com.quiet.bad\",\"label\":\"Quiet bad\",\"verdict\":\"BAD\"}")).andExpect(status().isOk());
        scan(t, null, app("com.quiet.bad", 0, false, false))
            .andExpect(jsonPath("$.results[0].verdict").value("MALICIOUS"))
            .andExpect(jsonPath("$.results[0].reasons[0]").value("Known harmful app"));
        mvc.perform(get("/api/v1/admin/apps?verdict=BAD").header("Authorization", adm))
            .andExpect(jsonPath("$[*].packageName", hasItem("com.quiet.bad")));
        mvc.perform(get("/api/v1/admin/apps?verdict=GOOD").header("Authorization", adm))
            .andExpect(jsonPath("$[*].packageName", hasItem("com.whatsapp")));
    }

    @Test
    void unknownPackageIsScoredNormally() throws Exception {
        scan(user(), null, app("com.unknown.spam", 30, true, true))
            .andExpect(jsonPath("$.results[0].verdict").value("MALICIOUS"));
    }

    // ---------- 6.3 ----------
    @Test
    void newRiskyAppIsMarkedOnceAndRecordedOnce() throws Exception {
        String t = user();
        String calm = app("com.calm.app", 1, false, false);
        String spam = app("com.new.spam", 30, true, true);
        scan(t, "dev-1", calm).andExpect(jsonPath("$.results[0].newSinceLastScan").value(false));
        scan(t, "dev-1", calm, spam)
            .andExpect(jsonPath("$.results[0].newSinceLastScan").value(false))
            .andExpect(jsonPath("$.results[1].newSinceLastScan").value(true))
            .andExpect(jsonPath("$.results[1].verdict").value("MALICIOUS"));
        scan(t, "dev-1", calm, spam).andExpect(jsonPath("$.results[1].newSinceLastScan").value(false));
        mvc.perform(get("/api/v1/threats").header("Authorization", t))
            .andExpect(jsonPath("$[?(@.type=='AD_ABUSE')]", hasSize(1)));
    }

    @Test
    void firstScanMarksNothingNewButStillRecordsAMaliciousAppOnce() throws Exception {
        String t = user();
        String spam = app("com.old.spam", 30, true, true);
        scan(t, "dev-2", spam).andExpect(jsonPath("$.results[0].newSinceLastScan").value(false));
        scan(t, "dev-2", spam);
        mvc.perform(get("/api/v1/threats").header("Authorization", t))
            .andExpect(jsonPath("$[?(@.type=='AD_ABUSE')]", hasSize(1)));
    }
}
