package com.fraudshield.service;

import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.ScanResponse;
import com.fraudshield.lookalike.LookalikeMatcher.Kind;
import com.fraudshield.lookalike.LookalikeMatcher.Match;
import com.fraudshield.lookalike.LookalikeService;
import com.fraudshield.lookalike.NormalizedDomain;
import com.fraudshield.repo.Repositories.BlocklistRepository;
import com.fraudshield.explain.ExplainService;
import com.fraudshield.ml.MlScoreService;
import com.fraudshield.siteintel.SiteIntel;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.*;

/**
 * Link risk score (10.9). Layers, each adding to one explainable score (reason + machine-readable reason code):
 * 1) the address itself (heuristics + 10.3 look-alike brand match) - instant, offline;
 * 2) domain age (10.5), certificate + redirects (10.6), the page itself (10.7 / 10.8) - {@link SiteIntel};
 * 3) external reputation (Safe Browsing).
 * Layers 2 and 3 run in parallel under one time budget (10.10): a slow layer is left out, never the whole scan.
 */
@Service
public class UrlScanService {
    /** 10.9: stable reason codes for the app and for statistics; the English reason text may change. */
    public static final class Codes {
        public static final String BLOCKLIST = "BLOCKLIST", MALFORMED = "MALFORMED_URL", NO_HOST = "NO_HOST",
                NO_HTTPS = "NO_HTTPS", RAW_IP = "RAW_IP", IDN = "LOOKALIKE_CHARACTERS", SHORTENER = "URL_SHORTENER",
                RISKY_TLD = "RISKY_TLD", MANY_SUBDOMAINS = "MANY_SUBDOMAINS", LONG_DOMAIN = "LONG_DOMAIN",
                MANY_HYPHENS = "MANY_HYPHENS", LOOKALIKE = "LOOKALIKE_BRAND", BRAND_SUBDOMAIN = "BRAND_IN_SUBDOMAIN",
                BRAND_PATH = "BRAND_IN_PATH", BAIT = "BAIT_WORDS", APK = "APK_DOWNLOAD", CREDENTIALS = "CREDENTIALS_TRICK",
                SAFE_BROWSING = "SAFE_BROWSING", ML_MODEL = "ML_MODEL";
        private Codes() {}
    }

    private static final Set<String> SHORTENERS = Set.of("bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd",
            "cutt.ly", "rb.gy", "shorturl.at", "tiny.cc");
    private static final Set<String> RISKY_TLDS = Set.of("xyz", "top", "click", "icu", "gq", "tk", "ml", "cf",
            "zip", "mov", "buzz", "monster", "rest", "cfd");
    private static final List<String> BAIT = List.of("login", "verify", "update", "free", "gift", "prize",
            "winner", "reward", "kyc", "suspend", "locked", "claim", "refund", "otp");

    private final BlocklistRepository blocklist;
    private final ReputationProvider reputation;
    private final LookalikeService lookalike;
    private final SiteIntel deep;          // null = off (tests, or fraudshield.urlscan.deep.enabled=false)
    private final java.time.Duration budget;
    private final MlScoreService ml;       // 11.4: null / disabled = rules only

    @org.springframework.beans.factory.annotation.Autowired
    public UrlScanService(BlocklistRepository blocklist, ReputationProvider reputation, LookalikeService lookalike,
                          org.springframework.beans.factory.ObjectProvider<SiteIntel> deep,
                          org.springframework.beans.factory.ObjectProvider<MlScoreService> ml,
                          @org.springframework.beans.factory.annotation.Value("${fraudshield.urlscan.budget-ms:3000}") long budgetMs) {
        this(blocklist, reputation, lookalike, deep.getIfAvailable(), java.time.Duration.ofMillis(budgetMs), ml.getIfAvailable());
    }

    public UrlScanService(BlocklistRepository blocklist, ReputationProvider reputation, LookalikeService lookalike,
                          SiteIntel deep, java.time.Duration budget) {
        this(blocklist, reputation, lookalike, deep, budget, MlScoreService.defaults());
    }

    public UrlScanService(BlocklistRepository blocklist, ReputationProvider reputation, LookalikeService lookalike,
                          SiteIntel deep, java.time.Duration budget, MlScoreService ml) {
        this.blocklist = blocklist; this.reputation = reputation; this.lookalike = lookalike; this.deep = deep;
        this.budget = budget; this.ml = ml;
    }

    public UrlScanService(BlocklistRepository blocklist, ReputationProvider reputation) {
        this(blocklist, reputation, LookalikeService.defaults(), null, java.time.Duration.ofSeconds(3));
    }

    public UrlScanService(BlocklistRepository blocklist) { this(blocklist, new NoopReputationProvider()); }

    /** Collects score, reasons and codes of one scan. */
    private static final class Result {
        int score;
        final List<String> reasons = new ArrayList<>(), codes = new ArrayList<>();
        String brand, officialUrl;
        Integer mlScore;
        boolean trusted;   // 11.5: official brand domain / restricted registry - never flagged (blocklist excepted)
        void add(int points, String code, String reason) { score += points; codes.add(code); reasons.add(reason); }
        ScanResponse done() {
            int s = Math.min(100, score);
            if (trusted && !codes.contains(Codes.BLOCKLIST)) s = Math.min(s, 20);
            return new ScanResponse(Verdict.fromScore(s), s, reasons, codes, brand, officialUrl, mlScore, null);
        }
    }

    private ExplainService explainer = TEMPLATES;
    private static final ExplainService TEMPLATES = new ExplainService(
            com.fraudshield.lookalike.BrandRegistry.seedFromClasspath().stream().map(b -> b.name()).collect(java.util.stream.Collectors.toSet()), null);

    /** 11.6: Spring passes the configured explainer (Claude when a key is set); plain-Java users get templates. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setExplainer(ExplainService e) { if (e != null) this.explainer = e; }

    /** Verdict + "why" in English and Hindi. */
    public ScanResponse scan(String rawUrl) {
        ScanResponse r = decide(rawUrl);
        return r.withExplanation(explainer.explain(r.verdict().name(), r.reasonCodes(), r.impersonatedBrand()));
    }

    private ScanResponse decide(String rawUrl) {
        java.time.Instant deadline = java.time.Instant.now().plus(budget);
        Result r = new Result();
        URI uri;
        try {
            uri = URI.create(rawUrl.contains("://") ? rawUrl.trim() : "http://" + rawUrl.trim());
        } catch (IllegalArgumentException e) {
            r.add(50, Codes.MALFORMED, "Malformed URL");
            return r.done();
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        NormalizedDomain d = lookalike.normalize(rawUrl);
        if (host.isEmpty() && d != null) host = d.asciiHost(); // e.g. "_" in a host: java.net.URI gives no host
        if (host.isEmpty() || d == null) {
            r.add(50, Codes.NO_HOST, "No host in URL");
            return r.done();
        }

        if (blocklist.findByKindAndValue("DOMAIN", host).isPresent()
                || (!host.equals(d.registrable()) && blocklist.findByKindAndValue("DOMAIN", d.registrable()).isPresent())) {
            r.add(100, Codes.BLOCKLIST, "Domain is on blocklist");
            return r.done();
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) r.add(10, Codes.NO_HTTPS, "Not using HTTPS");
        if (d.ip()) r.add(35, Codes.RAW_IP, "Raw IP address instead of domain");
        if (d.idn()) r.add(30, Codes.IDN, "Punycode/lookalike characters in domain");
        if (SHORTENERS.contains(d.registrable()) || SHORTENERS.contains(host)) r.add(25, Codes.SHORTENER, "URL shortener hides destination");

        String tld = d.publicSuffix().substring(d.publicSuffix().lastIndexOf('.') + 1);
        if (!d.ip() && RISKY_TLDS.contains(tld)) r.add(20, Codes.RISKY_TLD, "Risky top-level domain ." + tld);
        if (d.subdomainDepth() >= 3) r.add(15, Codes.MANY_SUBDOMAINS, "Too many subdomains");
        if (host.length() > 40) r.add(10, Codes.LONG_DOMAIN, "Very long domain");
        if (host.chars().filter(c -> c == '-').count() >= 3) r.add(10, Codes.MANY_HYPHENS, "Many hyphens in domain");

        // 10.3: fake copy of a real brand's address?
        Optional<Match> addressMatch = lookalike.check(d, uri.getRawPath());
        addressMatch.ifPresent(m -> {
            if (m.kind() != Kind.PATH) { r.brand = m.brand().name(); r.officialUrl = m.officialUrl(); }
            String code = switch (m.kind()) {
                case SUBDOMAIN -> Codes.BRAND_SUBDOMAIN;
                case PATH -> Codes.BRAND_PATH;
                default -> Codes.LOOKALIKE;
            };
            r.add(m.score(), code, m.kind() == Kind.PATH ? m.reason()
                    : "Looks like " + m.brand().name() + " but is not " + m.brand().name() + ": " + m.reason());
        });

        String lowerFull = rawUrl.toLowerCase(Locale.ROOT);
        long baitHits = BAIT.stream().filter(lowerFull::contains).count();
        if (baitHits > 0) r.add((int) Math.min(25, baitHits * 10), Codes.BAIT, "Bait words in URL");
        if (lowerFull.endsWith(".apk") || lowerFull.contains(".apk?")) r.add(40, Codes.APK, "Link downloads an APK");
        if (uri.getUserInfo() != null) r.add(30, Codes.CREDENTIALS, "Credentials (@) trick in URL");

        // layers 2 + 3 in parallel, both bounded by the deadline
        var claimed = addressMatch.filter(m -> m.kind() != Kind.PATH).map(Match::brand).orElse(null);
        java.util.concurrent.CompletableFuture<Integer> ext = deep == null
                ? java.util.concurrent.CompletableFuture.completedFuture(externalReputation(rawUrl))
                : java.util.concurrent.CompletableFuture.supplyAsync(() -> externalReputation(rawUrl), deep.executor());
        if (deep != null && !deep.skip(d)) {
            var facts = deep.examine(d, rawUrl, claimed, deadline.minusMillis(100));
            for (var f : facts.findings()) r.add(f.points(), f.code(), f.reason());
            if (r.brand == null && facts.brand() != null) {
                r.brand = facts.brand().name();
                r.officialUrl = lookalike.registry().officialUrl(facts.brand());
            }
        }
        int points = 0;
        try {
            long ms = Math.max(1, java.time.Duration.between(java.time.Instant.now(), deadline).toMillis());
            points = ext.get(ms, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Safe Browsing too slow / failed: this layer is left out
        }
        if (points > 0) r.add(points, Codes.SAFE_BROWSING, "Listed by Google Safe Browsing");
        applyModel(r, d, rawUrl);
        return r.done();
    }

    /**
     * 11.5 policy: the model can only RAISE a score, and only for sites we do not already know. Official brand domains
     * and restricted registries (gov.in, bank.in ...) are never flagged; popular (top-100k) sites get no model points;
     * blocklist / Safe Browsing hits stay as they are.
     */
    private void applyModel(Result r, NormalizedDomain d, String rawUrl) {
        boolean official = lookalike.registry().officialBrand(d.registrable()).isPresent();
        r.trusted = official || com.fraudshield.lookalike.LookalikeMatcher.restricted(d.publicSuffix())
                || com.fraudshield.lookalike.LookalikeMatcher.cdnZone(d);
        if (r.trusted || ml == null || !ml.enabled() || lookalike.isPopular(d.registrable())) return;
        Double p = ml.probability(rawUrl);
        if (p == null) return;
        r.mlScore = (int) Math.round(p * 100);
        if (p >= ml.threshold())
            r.add(p >= 0.99 ? 45 : 35, Codes.ML_MODEL, "Looks like known phishing links (AI model, " + r.mlScore + "% sure)");
    }

    /** Asks the reputation provider about the full URL. A provider failure counts as 0: the scan still works. */
    protected int externalReputation(String url) {
        Integer s = reputation.score(url);
        return s == null ? 0 : s;
    }
}
