package com.fraudshield.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VirusTotal API v3 file lookup: GET {base}/files/{sha256} with header x-apikey.
 * Facts recorded 2026-11 (re-check before launch, see https://docs.virustotal.com):
 *  - the public (free) API key is for NON-commercial use, about 4 requests/minute and 500/day;
 *  - 404 means VirusTotal has never seen the hash; the answer holds last_analysis_stats with malicious/harmless/... counts;
 *  - we send the hash only, never the file.
 * The client keeps below the free limit (4/min), caches answers 24 h by hash, never caches errors, and returns empty
 * on any failure so a scan never fails because VirusTotal is down.
 */
public class VirusTotalClient implements FileReputationClient {
    private static final Logger log = LoggerFactory.getLogger(VirusTotalClient.class);
    private static final Pattern STAT = Pattern.compile("\"(malicious|suspicious|harmless|undetected)\"\\s*:\\s*(\\d+)");

    private final String baseUrl;
    private final String apiKey;
    private final int maxPerMinute;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final Cache<String, Result> cache = Caffeine.newBuilder().maximumSize(20_000).expireAfterWrite(Duration.ofHours(24)).build();
    private final ArrayDeque<Long> sent = new ArrayDeque<>();

    public VirusTotalClient(String baseUrl, String apiKey, int maxPerMinute) {
        this.baseUrl = baseUrl; this.apiKey = apiKey; this.maxPerMinute = maxPerMinute;
    }

    @Override
    public Optional<Result> lookup(String sha256) {
        Result hit = cache.getIfPresent(sha256);
        if (hit != null) return Optional.of(hit);
        if (!tryAcquire()) { log.debug("VirusTotal skipped: local rate limit"); return Optional.empty(); }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/files/" + sha256))
                .timeout(Duration.ofSeconds(2)).header("x-apikey", apiKey).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) { Result unknown = new Result(0, 0); cache.put(sha256, unknown); return Optional.of(unknown); }
            if (res.statusCode() != 200) { log.warn("VirusTotal returned HTTP {}", res.statusCode()); return Optional.empty(); }
            Result r = parse(res.body());
            cache.put(sha256, r);
            return Optional.of(r);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("VirusTotal call failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static Result parse(String body) {
        int i = body.indexOf("last_analysis_stats");
        String part = i < 0 ? body : body.substring(i, Math.min(body.length(), i + 400));
        int malicious = 0, total = 0;
        Matcher m = STAT.matcher(part);
        while (m.find()) {
            int n = Integer.parseInt(m.group(2));
            total += n;
            if (m.group(1).equals("malicious")) malicious = n;
        }
        return new Result(malicious, total);
    }

    private synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        while (!sent.isEmpty() && now - sent.peekFirst() > 60_000) sent.pollFirst();
        if (sent.size() >= maxPerMinute) return false;
        sent.addLast(now);
        return true;
    }
}
