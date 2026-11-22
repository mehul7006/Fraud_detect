package com.fraudshield.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudshield.domain.Device;
import com.fraudshield.repo.Repositories.DeviceRepository;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 7.3: push alerts with Firebase Cloud Messaging (HTTP v1 API). Credentials = the Firebase service-account JSON
 * (FIREBASE_CREDENTIALS_FILE, a secret). The OAuth access token is made by signing a JWT with that key (RS256, the
 * jjwt library we already use) - no Firebase Admin SDK needed. Sending runs on its own thread so an API call is never
 * slowed down; a token Firebase reports as unregistered is removed. Without credentials the service is off.
 * Payloads carry NO personal data (no URLs, numbers or emails): they pass through Google's servers.
 */
@Service
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final DeviceRepository devices;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fcm-push"); t.setDaemon(true); return t;
    });
    private final ObjectMapper json = new ObjectMapper();
    private final String fcmBase;
    private String projectId, clientEmail, tokenUri;
    private PrivateKey key;
    private String accessToken;
    private Instant accessTokenExpires = Instant.EPOCH;

    public PushService(DeviceRepository devices,
                       @Value("${fraudshield.push.credentials-file:}") String credentialsFile,
                       @Value("${fraudshield.push.fcm-base:https://fcm.googleapis.com}") String fcmBase) {
        this.devices = devices;
        this.fcmBase = fcmBase;
        if (credentialsFile == null || credentialsFile.isBlank()) { log.info("Push alerts off (no FIREBASE_CREDENTIALS_FILE)"); return; }
        try {
            JsonNode c = json.readTree(Files.readString(Path.of(credentialsFile)));
            projectId = c.get("project_id").asText();
            clientEmail = c.get("client_email").asText();
            tokenUri = c.path("token_uri").asText("https://oauth2.googleapis.com/token");
            String pem = c.get("private_key").asText().replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            log.info("Push alerts on (Firebase project {})", projectId);
        } catch (Exception e) {
            log.warn("Push alerts off: credentials file unreadable ({})", e.getClass().getSimpleName());
            key = null;
        }
    }

    public boolean enabled() { return key != null; }

    /** Push to every device of the user that has a token. Never throws; returns at once. */
    public void notifyUser(Long userId, String title, String body, Map<String, String> data) {
        if (!enabled()) return;
        List<Device> targets = devices.findByUserId(userId).stream().filter(d -> d.getPushToken() != null).toList();
        if (targets.isEmpty()) return;
        worker.execute(() -> {
            for (Device d : targets) {
                try { send(d, title, body, data); }
                catch (IllegalStateException e) { log.warn("Push failed: {}", e.getMessage()); } // our own message, no PII
                catch (Exception e) { log.warn("Push failed: {}", e.getClass().getSimpleName()); }
            }
        });
    }

    private synchronized String accessToken() throws Exception {
        if (accessToken != null && Instant.now().isBefore(accessTokenExpires.minusSeconds(60))) return accessToken;
        Instant now = Instant.now();
        String assertion = Jwts.builder().issuer(clientEmail).claim("scope", SCOPE).audience().single(tokenUri)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(3600))).signWith(key, Jwts.SIG.RS256).compact();
        String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(tokenUri)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            // Google's error code only (e.g. invalid_grant) - never the assertion or the key
            String err = json.readTree(r.body()).path("error").asText("?");
            throw new IllegalStateException("token endpoint HTTP " + r.statusCode() + " " + err);
        }
        JsonNode t = json.readTree(r.body());
        accessToken = t.get("access_token").asText();
        accessTokenExpires = now.plusSeconds(t.path("expires_in").asLong(3600));
        return accessToken;
    }

    private void send(Device d, String title, String body, Map<String, String> data) throws Exception {
        var msg = json.createObjectNode();
        var m = msg.putObject("message");
        m.put("token", d.getPushToken());
        m.putObject("notification").put("title", title).put("body", body);
        m.putObject("android").put("priority", "HIGH").putObject("notification").put("channel_id", "fraudshield_critical");
        var dn = m.putObject("data");
        data.forEach(dn::put);
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(fcmBase + "/v1/projects/" + projectId + "/messages:send"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + accessToken())
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(msg))).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 404 || r.body().contains("UNREGISTERED")) {
            d.setPushToken(null); // app uninstalled / token rotated: stop sending to it
            devices.save(d);
        } else if (r.statusCode() / 100 != 2) {
            log.warn("FCM returned HTTP {} {}", r.statusCode(), json.readTree(r.body()).at("/error/status").asText("?"));
        }
    }
}
