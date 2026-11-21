package com.fraudshield.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Google Safe Browsing Lookup API v4 ({@code threatMatches:find}). Any failure returns null and logs the status only,
 * never the URL.
 */
public class SafeBrowsingProvider implements ReputationProvider {
    private static final Logger log = LoggerFactory.getLogger(SafeBrowsingProvider.class);
    static final int LISTED_SCORE = 60;

    private final String endpoint;
    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public SafeBrowsingProvider(String endpoint, String apiKey) { this.endpoint = endpoint; this.apiKey = apiKey; }

    @Override
    public Integer score(String url) {
        try {
            String body = "{\"client\":{\"clientId\":\"fraudshield\",\"clientVersion\":\"1.0\"},"
                + "\"threatInfo\":{\"threatTypes\":[\"MALWARE\",\"SOCIAL_ENGINEERING\",\"UNWANTED_SOFTWARE\","
                + "\"POTENTIALLY_HARMFUL_APPLICATION\"],\"platformTypes\":[\"ANY_PLATFORM\"],"
                + "\"threatEntryTypes\":[\"URL\"],\"threatEntries\":[{\"url\":\"" + escape(url) + "\"}]}}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint + "?key=" + apiKey))
                .timeout(Duration.ofSeconds(2)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Safe Browsing returned HTTP {}", res.statusCode());
                return null;
            }
            return res.body().contains("\"matches\"") ? LISTED_SCORE : 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("Safe Browsing call failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
