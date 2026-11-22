package com.fraudshield;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 7.3: push for server-detected threats, against a fake Firebase (token endpoint + FCM v1) in the test. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PushIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    static final KeyPair KEYS;
    static final HttpServer FAKE;
    static final List<String> tokenRequests = new CopyOnWriteArrayList<>();
    static final List<JsonNode> messages = new CopyOnWriteArrayList<>();
    static volatile String unregisteredToken = "";
    static Path creds;

    static {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA"); g.initialize(2048); KEYS = g.generateKeyPair();
            FAKE = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            FAKE.createContext("/token", ex -> {
                tokenRequests.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] b = "{\"access_token\":\"fake-access\",\"expires_in\":3600}".getBytes();
                ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close();
            });
            FAKE.createContext("/v1/projects/test-proj/messages:send", ex -> {
                JsonNode m = new ObjectMapper().readTree(ex.getRequestBody());
                boolean auth = "Bearer fake-access".equals(ex.getRequestHeaders().getFirst("Authorization"));
                messages.add(m);
                boolean gone = m.at("/message/token").asText().equals(unregisteredToken);
                byte[] b = (gone ? "{\"error\":{\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}" : "{\"name\":\"x\"}").getBytes();
                ex.sendResponseHeaders(!auth ? 401 : gone ? 404 : 200, b.length); ex.getResponseBody().write(b); ex.close();
            });
            FAKE.start();
            String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(KEYS.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n";
            creds = Files.createTempFile("fcm", ".json");
            Files.writeString(creds, new ObjectMapper().writeValueAsString(Map.of("type", "service_account", "project_id", "test-proj",
                    "client_email", "push@test-proj.iam.gserviceaccount.com", "private_key", pem,
                    "token_uri", "http://127.0.0.1:" + FAKE.getAddress().getPort() + "/token")));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("fraudshield.push.credentials-file", () -> creds.toString());
        r.add("fraudshield.push.fcm-base", () -> "http://127.0.0.1:" + FAKE.getAddress().getPort());
    }

    @AfterAll static void stop() { FAKE.stop(0); }
    @BeforeEach void clear() { messages.clear(); tokenRequests.clear(); unregisteredToken = ""; }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    private String user() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + UUID.randomUUID() + "@t.com\",\"password\":\"password123\"}")).andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body).get("token").asText();
    }

    private void device(String t, String id, String fp, String sim, String push) throws Exception {
        mvc.perform(post("/api/v1/devices").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceId\":\"" + id + "\",\"model\":\"Phone " + id + "\",\"fingerprint\":\"" + fp + "\",\"simHash\":\"" + sim
                        + "\",\"playProtectOn\":true,\"pushToken\":\"" + push + "\"}")).andExpect(status().isOk());
    }

    private void report(String t, String type) throws Exception {
        mvc.perform(post("/api/v1/threats").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\",\"severity\":\"HIGH\",\"title\":\"x\",\"sourceApp\":\"telegram\"}"));
    }

    private void waitFor(int n) throws InterruptedException {
        for (int i = 0; i < 100 && messages.size() < n; i++) Thread.sleep(50);
    }

    @Test
    void takeoverDetectedOnTheServer_isPushedToEveryDeviceOfTheUser_withoutPersonalData() throws Exception {
        String t = user();
        device(t, "mine", "fp1", "sim1", "token-mine");
        device(t, "new-phone", "fp2", "sim2", "token-new");
        report(t, "TELEGRAM_TAKEOVER"); // login code + new device -> ACCOUNT_TAKEOVER (source "correlation")
        waitFor(2);
        assertEquals(Set.of("token-mine", "token-new"), new HashSet<>(messages.stream().map(m -> m.at("/message/token").asText()).toList()));
        JsonNode m = messages.get(0).get("message");
        assertEquals("Account takeover in progress", m.at("/notification/title").asText());
        assertEquals("HIGH", m.at("/android/priority").asText());
        assertEquals("ACCOUNT_TAKEOVER", m.at("/data/type").asText());
        String all = messages.toString();
        assertFalse(all.contains("@t.com") || all.contains("Phone mine"), "no email / device details in the push: " + all);

        // the OAuth request is a JWT signed with the service-account key, for the messaging scope
        String assertion = java.net.URLDecoder.decode(tokenRequests.get(0).replaceAll(".*assertion=", ""), StandardCharsets.UTF_8);
        Claims c = Jwts.parser().verifyWith(KEYS.getPublic()).build().parseSignedClaims(assertion).getPayload();
        assertEquals("push@test-proj.iam.gserviceaccount.com", c.getIssuer());
        assertEquals("https://www.googleapis.com/auth/firebase.messaging", c.get("scope"));
        // Google's token endpoint answers invalid_grant when "aud" is a JSON list: it must be ONE string (seen live)
        String payload = new String(Base64.getUrlDecoder().decode(assertion.split("\\.")[1]), StandardCharsets.UTF_8);
        assertTrue(new ObjectMapper().readTree(payload).get("aud").isTextual(), "aud must be a single string: " + payload);
        assertTrue(new ObjectMapper().readTree(payload).get("exp").asLong() - new ObjectMapper().readTree(payload).get("iat").asLong() <= 3600);
    }

    @Test
    void threatsThePhoneReportedItself_areNotPushedBack() throws Exception {
        String t = user();
        device(t, "only", "fp1", "sim1", "token-only");
        report(t, "CALL_FORWARDING");
        Thread.sleep(500);
        assertTrue(messages.isEmpty(), "the phone already showed its own alert: " + messages);
    }

    @Test
    void anUnregisteredTokenIsForgotten() throws Exception {
        String t = user();
        device(t, "a", "fp1", "sim1", "token-old");
        device(t, "b", "fp2", "sim2", "token-b");
        unregisteredToken = "token-old";
        report(t, "WHATSAPP_TAKEOVER");
        waitFor(2);
        for (int i = 0; i < 40 && db.queryForObject("select count(*) from devices where push_token = 'token-old'", Integer.class) > 0; i++) Thread.sleep(50);
        assertEquals(0, db.queryForObject("select count(*) from devices where push_token = 'token-old'", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from devices where push_token = 'token-b'", Integer.class));
    }
}
