package com.fraudshield.siteintel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 10.5: domain facts from RDAP (the modern WHOIS): registration date, registrar, registrant country if published.
 * The registry's RDAP server comes from IANA's bootstrap file (loaded once a day; a copy ships as fallback).
 * Results are cached 24 h per registrable domain; a timeout or error never fails the scan (facts = unknown).
 * Only RDAP servers listed by IANA are contacted - never an address taken from the user's link.
 */
public final class DomainIntelService {
    /** {@code registered} null = unknown. */
    public record DomainFacts(String domain, Instant registered, String registrar, String country, boolean known) {
        public static DomainFacts unknown(String d) { return new DomainFacts(d, null, null, null, false); }
        public long ageDays(Instant now) { return registered == null ? -1 : Duration.between(registered, now).toDays(); }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http;
    private final String bootstrapUrl;
    private final Duration timeout;
    private final Cache<String, DomainFacts> cache = Caffeine.newBuilder().maximumSize(20_000)
            .expireAfterWrite(Duration.ofHours(24)).build();
    private volatile Map<String, String> servers;   // tld -> base url ending with '/'
    private volatile Instant serversLoaded = Instant.EPOCH;

    public DomainIntelService(String bootstrapUrl, Duration timeout) {
        this.bootstrapUrl = bootstrapUrl; this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
        this.servers = parseBootstrap(fallbackBootstrap());
    }

    public static DomainIntelService standard() { return new DomainIntelService("https://data.iana.org/rdap/dns.json", Duration.ofMillis(1500)); }

    /** Never throws; unknown facts when the registry has no RDAP, is slow, or answers rubbish. */
    public DomainFacts lookup(String registrable) {
        DomainFacts hit = cache.getIfPresent(registrable);
        if (hit != null) return hit;
        DomainFacts f = fetch(registrable);
        // remember "unknown" for a shorter time by not caching it: the next scan may succeed
        if (f.known()) cache.put(registrable, f);
        return f;
    }

    public DomainFacts cached(String registrable) { return cache.getIfPresent(registrable); }

    private DomainFacts fetch(String domain) {
        try {
            refreshServers();
            String tld = domain.substring(domain.lastIndexOf('.') + 1);
            String base = servers.get(tld);
            if (base == null) return DomainFacts.unknown(domain);
            var req = HttpRequest.newBuilder(URI.create(base + "domain/" + domain)).timeout(timeout)
                    .header("Accept", "application/rdap+json, application/json").GET().build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return DomainFacts.unknown(domain);
            return parse(domain, JSON.readTree(res.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DomainFacts.unknown(domain);
        } catch (Exception e) {
            return DomainFacts.unknown(domain);
        }
    }

    static DomainFacts parse(String domain, JsonNode root) {
        Instant reg = null;
        for (JsonNode e : root.path("events")) {
            if ("registration".equalsIgnoreCase(e.path("eventAction").asText())) {
                try { reg = Instant.parse(e.path("eventDate").asText()); } catch (Exception ignored) { /* odd date format */ }
            }
        }
        String registrar = null, country = null;
        for (JsonNode ent : root.path("entities")) {
            Set<String> roles = new HashSet<>();
            ent.path("roles").forEach(r -> roles.add(r.asText()));
            JsonNode vcard = ent.path("vcardArray").path(1);
            for (JsonNode prop : vcard) {
                String name = prop.path(0).asText();
                if (roles.contains("registrar") && name.equals("fn") && registrar == null) registrar = prop.path(3).asText(null);
                if (roles.contains("registrant") && name.equals("adr") && country == null) {
                    JsonNode adr = prop.path(3);
                    String c = adr.isArray() && adr.size() >= 7 ? adr.path(6).asText("") : "";
                    if (c.isEmpty()) c = prop.path(1).path("cc").asText("");
                    if (!c.isEmpty() && !c.toLowerCase(Locale.ROOT).contains("redact")) country = c;
                }
            }
        }
        return new DomainFacts(domain, reg, registrar, country, reg != null);
    }

    private void refreshServers() {
        if (Duration.between(serversLoaded, Instant.now()).toHours() < 24) return;
        serversLoaded = Instant.now(); // also on failure: do not retry on every scan
        try {
            var res = http.send(HttpRequest.newBuilder(URI.create(bootstrapUrl)).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                var m = parseBootstrap(res.body());
                if (m.size() > 100 || bootstrapUrl.startsWith("http://127.0.0.1") || bootstrapUrl.startsWith("http://localhost")) servers = m;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // keep the shipped copy
        }
    }

    static Map<String, String> parseBootstrap(String json) {
        Map<String, String> m = new HashMap<>();
        try {
            for (JsonNode svc : JSON.readTree(json).path("services")) {
                String url = null;
                for (JsonNode u : svc.path(1)) if (u.asText().startsWith("https://")) { url = u.asText(); break; }
                if (url == null && svc.path(1).size() > 0) url = svc.path(1).path(0).asText();
                if (url == null) continue;
                if (!url.endsWith("/")) url += "/";
                for (JsonNode t : svc.path(0)) m.put(t.asText().toLowerCase(Locale.ROOT), url);
            }
        } catch (Exception ignored) {
            // empty map
        }
        return m;
    }

    private static String fallbackBootstrap() {
        try (var in = DomainIntelService.class.getResourceAsStream("/seed/rdap-dns.json")) {
            return in == null ? "{}" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Points for the scan: very new / new / young domain. */
    public static Optional<Finding> score(DomainFacts f, Instant now) {
        long age = f.ageDays(now);
        if (age < 0) return Optional.empty();
        if (age < 7) return Optional.of(new Finding(35, "NEW_DOMAIN", "Website was created only " + age + " day(s) ago"));
        if (age < 30) return Optional.of(new Finding(25, "NEW_DOMAIN", "Website was created only " + age + " days ago"));
        if (age < 180) return Optional.of(new Finding(10, "YOUNG_DOMAIN", "Website is less than 6 months old"));
        return Optional.empty();
    }
}
