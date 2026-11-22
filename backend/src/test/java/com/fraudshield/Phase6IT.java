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
        return app(pkg, notif, overlay, fullScreen, null, null);
    }

    private static String app(String pkg, int notif, boolean overlay, boolean fullScreen, String cert, String installer) {
        return "{\"packageName\":\"" + pkg + "\",\"label\":\"x\",\"notifPerHour\":" + notif
            + ",\"hasOverlayPermission\":" + overlay + ",\"hasFullScreenIntent\":" + fullScreen
            + (cert == null ? "" : ",\"certSha256\":\"" + cert + "\"")
            + (installer == null ? "" : ",\"installer\":\"" + installer + "\"") + "}";
    }

    static final String PLAY = "com.android.vending";
    static final String REAL = "aa".repeat(32), OTHER = "bb".repeat(32);

    private String admin() throws Exception {
        login("boss6@t.com");
        bootstrap.promote();
        return login("boss6@t.com");
    }

    private ResultActions scan(String t, String deviceId, String... apps) throws Exception {
        String dev = deviceId == null ? "" : ",\"deviceId\":\"" + deviceId + "\"";
        return mvc.perform(post("/api/v1/scan/apps").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"apps\":[" + String.join(",", apps) + "]" + dev + "}"));
    }

    // ---------- 6.2 ----------
    @Autowired com.fraudshield.repo.Repositories.AppReputationRepository reputations;

    @Test
    void seedLoadsAtLeast500KnownGoodAppsAndEachIsTrustedFromPlay() throws Exception {
        var good = reputations.findAll().stream().filter(r -> "GOOD".equals(r.getVerdict())).toList();
        org.junit.jupiter.api.Assertions.assertTrue(good.size() >= 500, "seeded known-good apps: " + good.size());
        String t = user();
        // a spread of the list (social, bank, government, game): Play-installed + heavy notifications -> still SAFE
        for (String pkg : new String[]{"com.instagram.android", "com.sbi.lotusintouch", "com.digilocker.android",
                "com.supercell.clashofclans", "com.grofers.customerapp"}) {
            scan(t, null, app(pkg, 30, true, true, OTHER, PLAY))
                .andExpect(jsonPath("$.results[0].verdict").value("SAFE"))
                .andExpect(jsonPath("$.results[0].adAbuseScore").value(0));
        }
    }

    @Test
    void knownGoodAppIsNeverFlaggedEvenWithManyNotifications() throws Exception {
        scan(user(), null, app("com.whatsapp", 30, true, true, OTHER, PLAY))
            .andExpect(jsonPath("$.results[0].verdict").value("SAFE"))
            .andExpect(jsonPath("$.results[0].adAbuseScore").value(0));
    }

    @Test
    void sideloadedCopyWithAKnownGoodNameIsScoredNormally() throws Exception {
        scan(user(), null, app("com.whatsapp", 30, true, true, OTHER, "com.android.packageinstaller"))
            .andExpect(jsonPath("$.results[0].verdict").value("MALICIOUS"))
            .andExpect(jsonPath("$.results[0].reasons", hasItem("Can draw over other apps")));
        scan(user(), null, app("com.whatsapp", 30, true, true)) // client that sends no proof: not trusted by name
            .andExpect(jsonPath("$.results[0].verdict").value("MALICIOUS"));
    }

    @Test
    void knownCertificate_realAppSafe_lookAlikeFlaggedAsFake() throws Exception {
        String adm = admin();
        mvc.perform(post("/api/v1/admin/apps").header("Authorization", adm).contentType(MediaType.APPLICATION_JSON)
            .content("{\"packageName\":\"com.real.bank\",\"label\":\"Real Bank\",\"verdict\":\"GOOD\",\"certSha256\":\""
                + REAL.toUpperCase().replaceAll("(..)(?!$)", "$1:") + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.certSha256").value(REAL));
        String t = user();
        scan(t, null, app("com.real.bank", 30, true, true, REAL, null))
            .andExpect(jsonPath("$.results[0].verdict").value("SAFE"));
        scan(t, null, app("com.real.bank", 0, false, false, OTHER, PLAY))
            .andExpect(jsonPath("$.results[0].verdict").value("MALICIOUS"))
            .andExpect(jsonPath("$.results[0].reasons[0]").value("Fake copy of Real Bank: not signed by the real developer"));
        mvc.perform(post("/api/v1/admin/apps").header("Authorization", adm).contentType(MediaType.APPLICATION_JSON)
            .content("{\"packageName\":\"com.real.bank\",\"verdict\":\"GOOD\",\"certSha256\":\"nothex\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void csvImportAddsUpdatesAndReportsBadRows() throws Exception {
        String adm = admin();
        String csv = "package_name,label,verdict,cert_sha256\n"
            + "# comment\n"
            + "com.imp.one,Import One,GOOD," + REAL + "\n"
            + "com.imp.two,Import Two,BAD,\n"
            + "com.imp.three,Import Three,,\n"
            + "not a package,x,GOOD,\n"
            + "com.imp.four,x,MAYBE,\n"
            + "com.imp.five,x,GOOD,123\n";
        var file = new org.springframework.mock.web.MockMultipartFile("file", "apps.csv", "text/csv", csv.getBytes());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/admin/apps/import")
                .file(file).param("source", "top500-test").header("Authorization", adm))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.added").value(3))
            .andExpect(jsonPath("$.skipped").value(3))
            .andExpect(jsonPath("$.errors", hasSize(3)));
        String t = user();
        scan(t, null, app("com.imp.two", 0, false, false)).andExpect(jsonPath("$.results[0].verdict").value("MALICIOUS"));
        scan(t, null, app("com.imp.one", 30, true, true, REAL, null)).andExpect(jsonPath("$.results[0].verdict").value("SAFE"));
        scan(t, null, app("com.imp.three", 30, true, true, OTHER, PLAY)).andExpect(jsonPath("$.results[0].verdict").value("SAFE"));
        // re-import updates instead of duplicating
        var again = new org.springframework.mock.web.MockMultipartFile("file", "apps.csv", "text/csv",
            "com.imp.two,Import Two,GOOD,\n".getBytes());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/admin/apps/import")
                .file(again).header("Authorization", adm))
            .andExpect(jsonPath("$.updated").value(1)).andExpect(jsonPath("$.added").value(0));
        // and normal users cannot import
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/admin/apps/import")
                .file(again).header("Authorization", t))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminMarksPackageBadAndItIsAlwaysMalicious() throws Exception {
        String t = user();
        scan(t, null, app("com.quiet.bad", 0, false, false)).andExpect(jsonPath("$.results[0].verdict").value("SAFE"));
        mvc.perform(post("/api/v1/admin/apps").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"packageName\":\"com.quiet.bad\",\"verdict\":\"BAD\"}")).andExpect(status().isForbidden());
        String adm = admin();
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

    // ---------- 6.1 ----------
    @Test
    void optInLabelsAreStoredOncePerAppAndExportedForTuning() throws Exception {
        String t = user();
        String spam = app("com.label.spam", 30, true, true);
        mvc.perform(post("/api/v1/scan/apps/label").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"app\":" + spam + ",\"label\":\"BENIGN\",\"scoreShown\":90}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/scan/apps/label").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"app\":" + spam + ",\"label\":\"ABUSIVE\",\"scoreShown\":90}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/scan/apps/label").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"app\":" + app("com.label.chat", 9, false, false) + ",\"label\":\"BENIGN\",\"scoreShown\":30}"))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/scan/apps/label").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"app\":" + spam + ",\"label\":\"MAYBE\",\"scoreShown\":1}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/scan/apps/label").contentType(MediaType.APPLICATION_JSON)
            .content("{\"app\":" + spam + ",\"label\":\"ABUSIVE\",\"scoreShown\":1}")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/admin/apps/labels.csv").header("Authorization", t)).andExpect(status().isForbidden());
        String csv = mvc.perform(get("/api/v1/admin/apps/labels.csv").header("Authorization", admin()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var lines = csv.lines().filter(l -> l.startsWith("ABUSIVE,") || l.startsWith("BENIGN,")).toList();
        org.junit.jupiter.api.Assertions.assertTrue(lines.contains("ABUSIVE,com.label.spam,30,true,false,false,false,true,false,0"), csv);
        org.junit.jupiter.api.Assertions.assertTrue(lines.contains("BENIGN,com.label.chat,9,false,false,false,false,false,false,0"), csv);
        org.junit.jupiter.api.Assertions.assertEquals(1, lines.stream().filter(l -> l.contains("com.label.spam")).count(),
            "a later decision replaces the earlier one");
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("label,name,notifPerHour,overlay,sms,accessibility,listener,fullScreen,isSystem,selfLaunch"));
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
