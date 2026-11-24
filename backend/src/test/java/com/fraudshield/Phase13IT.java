package com.fraudshield;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.repo.Repositories.AppReputationRepository;
import com.fraudshield.repo.Repositories.ThreatEventRepository;
import com.fraudshield.service.BlocklistSyncService;
import com.fraudshield.service.DomainCategoryService;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Phase 13 backend against real PostgreSQL: 13.1 categories, 13.6 domain sync, 13.7 combined app scan, 13.13 registry. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Phase13IT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired BlocklistSyncService signer;
    @Autowired AppReputationRepository reputation;
    @Autowired ThreatEventRepository threats;
    static final ObjectMapper JSON = new ObjectMapper();

    private String user() throws Exception {
        String email = UUID.randomUUID() + "@t.com";
        String b = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + b.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private JsonNode fetch(String path, String t) throws Exception {
        return JSON.readTree(mvc.perform(get(path).header("Authorization", t)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void domain_categories_sync_signed_and_delta() throws Exception {
        String t = user();
        JsonNode full = fetch("/api/v1/config/domains?since=0", t);
        assertTrue(full.get("full").asBoolean());
        List<DomainCategoryService.Entry> added = new ArrayList<>();
        full.get("added").forEach(e -> added.add(new DomainCategoryService.Entry(e.get("domain").asText(), e.get("category").asText())));
        assertTrue(added.size() > 5000, "seeded: " + added.size());
        assertTrue(added.contains(new DomainCategoryService.Entry("doubleclick.net", "ads")));
        assertTrue(added.contains(new DomainCategoryService.Entry("app-measurement.com", "analytics")));
        var key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(signer.publicKeyBase64())));
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initVerify(key);
        s.update(DomainCategoryService.canonical(full.get("version").asLong(), true, added, List.of()).getBytes(StandardCharsets.UTF_8));
        assertTrue(s.verify(Base64.getDecoder().decode(full.get("signature").asText())), "signature verifies");
        JsonNode none = fetch("/api/v1/config/domains?since=" + full.get("version").asLong(), t);
        assertFalse(none.get("full").asBoolean());
        assertEquals(0, none.get("added").size(), "nothing changed since");
    }

    @Test
    void official_registry_and_categories() throws Exception {
        JsonNode apps = fetch("/api/v1/config/official-apps", user());
        assertTrue(apps.size() >= 100, "official apps: " + apps.size());
        boolean phonepe = false;
        for (JsonNode a : apps) if (a.get("packageName").asText().equals("com.phonepe.app") && a.get("brand").asText().equals("PhonePe")) phonepe = true;
        assertTrue(phonepe);
        assertEquals("MESSAGING", reputation.findByPackageName("com.whatsapp").orElseThrow().getCategory(), "13.1 category seeded");
    }

    @Test
    void combined_app_scan_new_and_old_clients() throws Exception {
        String t = user();
        long before = threats.count();
        String body = "{\"deviceId\":\"d13\",\"apps\":["
                + "{\"packageName\":\"com.phonepe.app.secure\",\"label\":\"PhonePe\",\"notifPerHour\":0,\"requestedPermissions\":[\"android.permission.READ_SMS\"],"
                + "\"grantedPermissions\":[\"android.permission.READ_SMS\"],\"targetSdk\":34,\"trackers\":[],\"domainsContacted\":[]},"
                + "{\"packageName\":\"com.whatsapp\",\"label\":\"WhatsApp\",\"installer\":\"com.android.vending\",\"requestedPermissions\":"
                + "[\"android.permission.READ_CONTACTS\",\"android.permission.READ_SMS\"],\"trackers\":[\"Facebook Analytics\"]},"
                + "{\"packageName\":\"com.example.torch\",\"label\":\"Bright Flashlight\",\"installer\":\"com.android.vending\","
                + "\"requestedPermissions\":[\"android.permission.CAMERA\",\"android.permission.READ_CONTACTS\",\"android.permission.READ_SMS\","
                + "\"android.permission.ACCESS_FINE_LOCATION\"],\"grantedPermissions\":[\"android.permission.READ_CONTACTS\",\"android.permission.READ_SMS\"]}]}";
        mvc.perform(post("/api/v1/scan/apps").header("Authorization", t).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$.results[0].impersonatedBrand").value("PhonePe"))
                .andExpect(jsonPath("$.results[0].reasonCodes", hasItem("FAKE_APP")))
                .andExpect(jsonPath("$.results[0].privacyScore", greaterThanOrEqualTo(70)))
                .andExpect(jsonPath("$.results[0].explanation.en", startsWith("This app pretends to be PhonePe")))
                .andExpect(jsonPath("$.results[1].verdict").value("SAFE"))
                .andExpect(jsonPath("$.results[1].privacyScore").value(0))
                .andExpect(jsonPath("$.results[2].verdict").value("SUSPICIOUS"))
                .andExpect(jsonPath("$.results[2].reasonCodes", hasItem("PERMISSION_NOT_NEEDED")))
                .andExpect(jsonPath("$.results[2].privacyReasons[0]", containsString("torch app asks for")));
        assertEquals(before + 1, threats.count(), "the fake app is recorded once");
        assertTrue(threats.findAll().stream().anyMatch(x -> x.getTitle().equals("Fake PhonePe app: com.phonepe.app.secure")));
        // an older client (6.x fields only) gets the 6.x answer, no privacy fields
        mvc.perform(post("/api/v1/scan/apps").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apps\":[{\"packageName\":\"com.spam.app\",\"label\":\"Spam\",\"notifPerHour\":30,\"hasOverlayPermission\":true,"
                                + "\"hasFullScreenIntent\":true}]}"))
                .andExpect(jsonPath("$.results[0].adAbuseScore", greaterThan(0)))
                .andExpect(jsonPath("$.results[0].privacyScore").doesNotExist());
    }
}
