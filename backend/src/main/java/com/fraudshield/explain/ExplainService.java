package com.fraudshield.explain;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 11.6 / 11.7: "why is this risky?" in English and Hindi, at most two sentences.
 *
 * <p>The verdict is decided by the rules and the model; this class only words it. Every scan gets the offline
 * template text at once. When an {@link Explainer} (Claude) is configured, a nicer wording is fetched in the
 * background, cached by the reason set, and used for the next scan with the same reasons.
 *
 * <p>Privacy / prompt-injection rule: the explainer only ever receives the verdict, reason CODES that exist in the
 * template files, and a brand name that exists in the brand list. Never the URL, the domain, page text or anything
 * the user or a website wrote - so a hostile page title cannot reach the prompt.
 */
public final class ExplainService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ExplainService.class);

    /** What a writer (Claude) is given. All values come from closed lists. */
    public record Request(String verdict, List<String> codes, String brand) {}

    /** Writes an explanation; returns null (or throws) when it cannot - the template is used then. */
    public interface Explainer {
        Map<String, String> explain(Request r) throws Exception;
    }

    /** Order of importance for the template (first two that apply are used). */
    static final List<String> PRIORITY = List.of("BLOCKLIST", "SAFE_BROWSING", "COPIES_BRAND_LOGO", "COPIES_BRAND_PAGE",
            "ASKS_FOR_SECRETS", "FORM_TO_OTHER_DOMAIN", "PRIVATE_ADDRESS", "APK_DOWNLOAD", "LOOKALIKE_BRAND",
            "BRAND_IN_SUBDOMAIN", "LOOKALIKE_CHARACTERS", "CREDENTIALS_TRICK", "NEW_DOMAIN", "CERT_MISMATCH",
            "CERT_UNTRUSTED", "REDIRECTS_ELSEWHERE", "SCAM_WORDING", "ML_MODEL", "RAW_IP", "BAIT_WORDS",
            "PASSWORD_OVER_HTTP", "TOO_MANY_REDIRECTS", "URL_SHORTENER", "BRAND_IN_PATH", "META_REFRESH_AWAY",
            "HTTPS_DOWNGRADE", "NEW_CERTIFICATE", "YOUNG_DOMAIN", "RISKY_TLD", "MANY_SUBDOMAINS", "MANY_HYPHENS",
            "LONG_DOMAIN", "NO_HTTPS", "MALFORMED_URL", "NO_HOST");

    private final Properties en, hi;
    private final Set<String> brands;
    private final Explainer writer;
    private final Cache<String, Map<String, String>> cache = Caffeine.newBuilder().maximumSize(5_000)
            .expireAfterWrite(Duration.ofDays(30)).build();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    /** @param brands every allowed brand name (others are dropped); @param writer null = templates only */
    public ExplainService(Set<String> brands, Explainer writer) {
        this.en = load("en"); this.hi = load("hi"); this.brands = Set.copyOf(brands); this.writer = writer;
    }

    public Set<String> knownCodes() {
        Set<String> s = new TreeSet<>();
        for (String k : en.stringPropertyNames()) if (!k.startsWith("lead.")) s.add(k);
        return s;
    }

    public boolean hasText(String code, String lang) { return (lang.equals("hi") ? hi : en).getProperty(code) != null; }

    /** {en, hi}; null for SAFE. Never blocks on the network. */
    public Map<String, String> explain(String verdict, List<String> codes, String brand) {
        if (verdict == null || verdict.equals("SAFE")) return null;
        Request req = clean(verdict, codes, brand);
        String key = key(req);
        Map<String, String> cached = cache.getIfPresent(key);
        if (cached != null) return cached;
        if (writer != null && inFlight.add(key)) {
            pool.submit(() -> {
                try {
                    Map<String, String> m = writer.explain(req);
                    if (valid(m)) cache.put(key, Map.of("en", m.get("en").strip(), "hi", m.get("hi").strip(), "source", "ai"));
                } catch (Exception e) {
                    log.debug("explainer failed, template stays: {}", e.toString());
                } finally {
                    inFlight.remove(key);
                }
            });
        }
        return template(req);
    }

    /** Only known codes, only a known brand - nothing else can reach a prompt. */
    Request clean(String verdict, List<String> codes, String brand) {
        Set<String> known = knownCodes();
        List<String> c = codes == null ? List.of() : codes.stream().filter(known::contains).distinct().sorted().toList();
        String v = verdict.equals("MALICIOUS") ? "MALICIOUS" : "SUSPICIOUS";
        return new Request(v, c, brand != null && brands.contains(brand) ? brand : null);
    }

    static String key(Request r) { return r.verdict() + "|" + String.join(",", r.codes()) + "|" + Objects.toString(r.brand(), ""); }

    Map<String, String> template(Request r) {
        return Map.of("en", sentences(en, r), "hi", sentences(hi, r), "source", "template");
    }

    private String sentences(Properties p, Request r) {
        List<String> ordered = new ArrayList<>();
        for (String c : PRIORITY) if (r.codes().contains(c)) ordered.add(c);
        for (String c : r.codes()) if (!ordered.contains(c)) ordered.add(c);
        boolean fakeApp = r.brand() != null && ordered.remove("FAKE_APP"); // 13.x: an app, not a link
        String first = fakeApp ? p.getProperty("FAKE_APP") : r.brand() != null ? p.getProperty("lead.brand")
                : ordered.isEmpty() ? p.getProperty(r.verdict().equals("MALICIOUS") ? "lead.malicious" : "lead.suspicious")
                : p.getProperty(ordered.remove(0));
        if (r.brand() != null) ordered.removeAll(List.of("LOOKALIKE_BRAND", "COPIES_BRAND_PAGE", "BRAND_IN_SUBDOMAIN"));
        String second = ordered.isEmpty() ? "" : " " + p.getProperty(ordered.get(0));
        return (first + second).replace("{brand}", r.brand() == null ? "" : r.brand()).strip();
    }

    /** An AI text must be short, in both languages, and free of links / markup. */
    static boolean valid(Map<String, String> m) {
        if (m == null) return false;
        for (String lang : List.of("en", "hi")) {
            String t = m.get(lang);
            if (t == null || t.isBlank() || t.length() > 400) return false;
            if (t.contains("http") || t.contains("www.") || t.contains("<") || t.contains("{")) return false;
            if (t.split("(?<=[.!?।])\\s+").length > 2) return false;
        }
        return true;
    }

    private static Properties load(String lang) {
        Properties p = new Properties();
        try (var in = ExplainService.class.getResourceAsStream("/explain/explanations_" + lang + ".properties")) {
            p.load(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("explanations_" + lang + " missing", e);
        }
        return p;
    }

    @Override
    public void close() { pool.shutdownNow(); }
}
