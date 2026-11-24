package com.fraudshield.service;

import com.fraudshield.domain.OfficialApp;
import com.fraudshield.repo.Repositories.OfficialAppRepository;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 13.13: brand -> its official Android packages (+ signing certificate when verified). Answers "is this package an
 * official app?" and "does this app's NAME claim to be a brand it does not belong to?" (fake app, 13.12).
 * Seeded from seed/official-apps.csv (adds missing rows only); lookups are in memory.
 */
@Service
@Order(20)
public class OfficialAppService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(OfficialAppService.class);

    public record Official(String brand, String category, String packageName, String playTitle, String certSha256) {}

    /** Words that do not change which brand a name claims ("PhonePe UPI Payments" = PhonePe). */
    private static final Set<String> GENERIC = Set.of("app", "apps", "upi", "pay", "payment", "payments", "online", "shopping",
            "shop", "mobile", "banking", "bank", "india", "official", "secure", "lite", "new", "the", "for", "and", "&", "plus",
            "pro", "loan", "loans", "wallet", "money", "messenger", "business", "go", "android", "free", "service", "services",
            "recharge", "bill", "bills", "store", "kyc", "update", "support", "customer", "care", "help", "rewards", "cashback");

    private final OfficialAppRepository repo; // null in plain unit tests
    private volatile Map<String, Official> byPackage = Map.of();
    private volatile Map<String, String> brandByName = Map.of(); // normalised brand name / Play title -> brand

    @Autowired
    public OfficialAppService(OfficialAppRepository repo) { this.repo = repo; use(seed()); }

    /** Plain unit tests: the seed file only. */
    public OfficialAppService() { this(null); }

    public Optional<Official> byPackage(String pkg) { return Optional.ofNullable(byPackage.get(pkg)); }
    public int size() { return byPackage.size(); }
    public List<Official> all() { return byPackage.values().stream().sorted(Comparator.comparing(Official::brand).thenComparing(Official::packageName)).toList(); }
    public List<Official> ofBrand(String brand) { return byPackage.values().stream().filter(o -> o.brand().equals(brand)).toList(); }

    /** The brand an app's display name claims to be, or empty ("Status Saver for WhatsApp" claims nothing). */
    public Optional<String> claimedBrand(String label) {
        if (label == null || label.isBlank()) return Optional.empty();
        return Optional.ofNullable(brandByName.get(normalise(label)));
    }

    /** The brand this app pretends to be: its name claims a brand but its package is none of that brand's apps. */
    public Optional<String> fakeOf(String label, String pkg) {
        var claimed = claimedBrand(label);
        if (claimed.isEmpty() || byPackage.containsKey(pkg)) return Optional.empty();
        return claimed;
    }

    static String normalise(String s) {
        String t = s.toLowerCase(Locale.ROOT).replaceAll("\\(.*?\\)", " ").replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        List<String> words = new ArrayList<>();
        for (String w : t.split(" ")) if (!w.isEmpty() && !GENERIC.contains(w)) words.add(w);
        return String.join(" ", words);
    }

    private void use(List<Official> list) {
        Map<String, Official> pk = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (var o : list) {
            pk.put(o.packageName(), o);
            String bn = normalise(o.brand());
            if (bn.length() >= 3) names.putIfAbsent(bn, o.brand()); // "X (Twitter)" -> "x": too short to be a claim
            for (String part : o.brand().split("/|\\(|\\)")) { // "BHIM UPI / NPCI" -> "bhim", "npci"; "Vi (Vodafone Idea)"
                String n = normalise(part);
                if (n.length() >= 4) names.putIfAbsent(n, o.brand());
            }
            if (o.playTitle() != null && !o.playTitle().isBlank()) {
                // "YONO SBI: Banking - UPI & Pay" -> "yono sbi"; the part before ':' / ' - ' is the app's name
                String head = o.playTitle().split(":| - |\\|")[0];
                String n = normalise(head);
                if (n.length() >= 3) names.putIfAbsent(n, o.brand());
            }
        }
        byPackage = Map.copyOf(pk);
        brandByName = Map.copyOf(names);
    }

    static List<Official> seed() {
        try (InputStream in = OfficialAppService.class.getResourceAsStream("/seed/official-apps.csv")) {
            if (in == null) return List.of();
            List<Official> out = new ArrayList<>();
            for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\r?\\n")) {
                if (l.isBlank() || l.startsWith("#") || l.startsWith("brand,")) continue;
                String[] c = l.split(",", -1);
                if (c.length < 3) continue;
                out.add(new Official(c[0].strip(), c[1].strip(), c[2].strip(), c.length > 3 ? c[3].strip() : null,
                        c.length > 4 && !c[4].isBlank() ? AppTrust.normalise(c[4]) : null));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("seed/official-apps.csv unreadable", e);
        }
    }

    /** Start-up: add missing seed rows to the table, then serve from the table (admin rows included). */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repo == null) return;
        int added = 0;
        for (var o : seed()) {
            if (repo.findByPackageName(o.packageName()).isPresent()) continue;
            repo.save(new OfficialApp(o.brand(), o.category(), o.packageName(), o.playTitle(), o.certSha256(), "seed-2026-11"));
            added++;
        }
        if (added > 0) log.info("Official apps: {} seeded", added);
        use(repo.findAll().stream().map(r -> new Official(r.getBrand(), r.getCategory(), r.getPackageName(), r.getPlayTitle(), r.getCertSha256())).toList());
    }
}
