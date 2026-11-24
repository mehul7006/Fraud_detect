package com.fraudshield;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.config.AdminBootstrap;
import com.fraudshield.repo.Repositories.ThreatEventRepository;
import com.fraudshield.service.BlocklistSyncService;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 12.5 signed blocklist deltas and 12.6 the VPN's domain check, against real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {"fraudshield.admin-email=vpnboss@t.com", "logging.level.com.fraudshield=DEBUG"})
class Phase12IT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired AdminBootstrap bootstrap;
    @Autowired BlocklistSyncService sync;
    @Autowired ThreatEventRepository threats;
    static final ObjectMapper JSON = new ObjectMapper();

    private String login(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"));
        String b = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + b.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private String admin() throws Exception {
        login("vpnboss@t.com");
        bootstrap.promote();
        return login("vpnboss@t.com");
    }

    private JsonNode get(String path, String token) throws Exception {
        return JSON.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static List<String> list(JsonNode a) { List<String> l = new ArrayList<>(); a.forEach(x -> l.add(x.asText())); return l; }

    /** What the app does: rebuild the canonical text and check the signature with the public key. */
    private boolean verifies(JsonNode d, List<String> added) throws Exception {
        var key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(sync.publicKeyBase64())));
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initVerify(key);
        s.update(BlocklistSyncService.canonical(d.get("version").asLong(), d.get("full").asBoolean(), added, list(d.get("removed")))
                .getBytes(StandardCharsets.UTF_8));
        return s.verify(Base64.getDecoder().decode(d.get("signature").asText()));
    }

    @Test
    void signed_full_list_then_delta_only() throws Exception {
        String adm = admin();
        for (String dom : new String[]{"vpn-fake-one.test", "vpn-fake-two.test"})
            mvc.perform(post("/api/v1/admin/blocklist").header("Authorization", adm).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"kind\":\"DOMAIN\",\"value\":\"" + dom + "\",\"reason\":\"t\"}")).andExpect(status().isCreated());
        String user = login(UUID.randomUUID() + "@t.com");
        JsonNode full = get("/api/v1/config/blocklist?since=0", user);
        assertTrue(full.get("full").asBoolean());
        List<String> added = list(full.get("added"));
        assertTrue(added.contains(BlocklistSyncService.hash("vpn-fake-one.test")));
        assertTrue(added.contains(BlocklistSyncService.hash("vpn-fake-two.test")));
        assertFalse(full.toString().contains("vpn-fake"), "only hashes leave the server");
        assertTrue(verifies(full, added));
        List<String> tampered = new ArrayList<>(added);
        tampered.remove(0);
        assertFalse(verifies(full, tampered), "a changed list must not verify");

        long v = full.get("version").asLong();
        String list = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/blocklist").header("Authorization", adm))
                .andReturn().getResponse().getContentAsString();
        String id = list.replaceAll(".*\\{\"id\":(\\d+),\"kind\":\"DOMAIN\",\"value\":\"vpn-fake-one\\.test\".*", "$1");
        mvc.perform(delete("/api/v1/admin/blocklist/" + id).header("Authorization", adm)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/admin/blocklist").header("Authorization", adm).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"DOMAIN\",\"value\":\"vpn-fake-three.test\",\"reason\":\"t\"}")).andExpect(status().isCreated());
        JsonNode delta = get("/api/v1/config/blocklist?since=" + v, user);
        assertFalse(delta.get("full").asBoolean());
        assertEquals(List.of(BlocklistSyncService.hash("vpn-fake-three.test")), list(delta.get("added")), "only the new entry");
        assertEquals(List.of(BlocklistSyncService.hash("vpn-fake-one.test")), list(delta.get("removed")));
        assertTrue(verifies(delta, list(delta.get("added"))));
        assertEquals(0, list(get("/api/v1/config/blocklist?since=" + delta.get("version").asLong(), user).get("added")).size(), "nothing new");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/config/blocklist")).andExpect(status().isUnauthorized());
    }

    @Test
    void domain_check_is_domain_only_and_leaves_no_trace(CapturedOutput out) throws Exception {
        String user = login(UUID.randomUUID() + "@t.com");
        long before = threats.count();
        mvc.perform(post("/api/v1/scan/domain").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":\"fleepkart.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$.impersonatedBrand").value("Flipkart"))
                .andExpect(jsonPath("$.officialUrl").value("https://www.flipkart.com"))
                .andExpect(jsonPath("$.reasonCodes", hasItem("LOOKALIKE_BRAND")))
                .andExpect(jsonPath("$.reasonCodes", not(hasItem("NO_HTTPS"))));
        mvc.perform(post("/api/v1/scan/domain").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"domain\":\"www.flipkart.com\"}")).andExpect(jsonPath("$.verdict").value("SAFE"));
        String secret = "privacy-check-" + UUID.randomUUID().toString().substring(0, 8) + ".xyz";
        mvc.perform(post("/api/v1/scan/domain").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"domain\":\"" + secret + "\"}")).andExpect(status().isOk());
        for (String bad : new String[]{"evil.xyz/login", "a b.com", "https://evil.xyz", "evil.xyz?x=1", ""})
            mvc.perform(post("/api/v1/scan/domain").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"domain\":\"" + bad + "\"}")).andExpect(status().isBadRequest());
        assertEquals(before, threats.count(), "no threat record: the domain is never linked to the user");
        assertFalse(out.getAll().contains(secret), "the domain never appears in the server log");
        assertFalse(out.getAll().contains("fleepkart.com"), "not even flagged ones");
    }
}
