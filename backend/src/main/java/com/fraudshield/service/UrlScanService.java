package com.fraudshield.service;

import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.ScanResponse;
import com.fraudshield.repo.Repositories.BlocklistRepository;
import org.springframework.stereotype.Service;
import java.net.IDN;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Heuristic URL risk scoring. Plug Google Safe Browsing / VirusTotal in {@link #externalReputation(String)} (v2).
 */
@Service
public class UrlScanService {
    private static final Set<String> SHORTENERS = Set.of("bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd",
            "cutt.ly", "rb.gy", "shorturl.at", "tiny.cc");
    private static final Set<String> RISKY_TLDS = Set.of("xyz", "top", "click", "icu", "gq", "tk", "ml", "cf",
            "zip", "mov", "buzz", "monster", "rest", "cfd");
    private static final Set<String> BRANDS = Set.of("whatsapp", "telegram", "paytm", "phonepe", "gpay", "sbi",
            "hdfc", "icici", "amazon", "flipkart", "facebook", "instagram", "google", "netflix", "aadhaar", "kyc");
    private static final List<String> BAIT = List.of("login", "verify", "update", "free", "gift", "prize",
            "winner", "reward", "kyc", "suspend", "locked", "claim", "refund", "otp");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private final BlocklistRepository blocklist;

    public UrlScanService(BlocklistRepository blocklist) { this.blocklist = blocklist; }

    public ScanResponse scan(String rawUrl) {
        List<String> reasons = new ArrayList<>();
        int score = 0;

        URI uri;
        try {
            uri = URI.create(rawUrl.contains("://") ? rawUrl.trim() : "http://" + rawUrl.trim());
        } catch (IllegalArgumentException e) {
            return new ScanResponse(Verdict.SUSPICIOUS, 50, List.of("Malformed URL"));
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) return new ScanResponse(Verdict.SUSPICIOUS, 50, List.of("No host in URL"));

        if (blocklist.findByKindAndValue("DOMAIN", host).isPresent()) {
            return new ScanResponse(Verdict.MALICIOUS, 100, List.of("Domain is on blocklist"));
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) { score += 10; reasons.add("Not using HTTPS"); }
        if (IPV4.matcher(host).matches()) { score += 35; reasons.add("Raw IP address instead of domain"); }
        if (host.startsWith("xn--") || !IDN.toUnicode(host).equals(host)) {
            score += 30; reasons.add("Punycode/lookalike characters in domain");
        }
        if (SHORTENERS.contains(host)) { score += 25; reasons.add("URL shortener hides destination"); }

        String tld = host.substring(host.lastIndexOf('.') + 1);
        if (RISKY_TLDS.contains(tld)) { score += 20; reasons.add("Risky top-level domain ." + tld); }

        int dots = host.split("\\.").length - 1;
        if (dots >= 4) { score += 15; reasons.add("Too many subdomains"); }
        if (host.length() > 40) { score += 10; reasons.add("Very long domain"); }
        if (host.chars().filter(c -> c == '-').count() >= 3) { score += 10; reasons.add("Many hyphens in domain"); }

        // brand name in domain but not the registrable domain (e.g. whatsapp.verify-login.xyz)
        String registrable = registrable(host);
        for (String b : BRANDS) {
            if (host.contains(b) && !registrable.startsWith(b + ".")) {
                score += 30; reasons.add("Imitates brand '" + b + "'"); break;
            }
        }
        String lowerFull = rawUrl.toLowerCase(Locale.ROOT);
        long baitHits = BAIT.stream().filter(lowerFull::contains).count();
        if (baitHits > 0) { score += (int) Math.min(25, baitHits * 10); reasons.add("Bait words in URL"); }
        if (lowerFull.endsWith(".apk") || lowerFull.contains(".apk?")) {
            score += 40; reasons.add("Link downloads an APK");
        }
        if (uri.getUserInfo() != null) { score += 30; reasons.add("Credentials (@) trick in URL"); }

        score += externalReputation(host);
        score = Math.min(100, score);
        return new ScanResponse(Verdict.fromScore(score), score, reasons);
    }

    private static String registrable(String host) {
        String[] p = host.split("\\.");
        return p.length <= 2 ? host : p[p.length - 2] + "." + p[p.length - 1];
    }

    /** Hook for Safe Browsing / VirusTotal. Return extra score 0-100. */
    protected int externalReputation(String host) { return 0; }
}
