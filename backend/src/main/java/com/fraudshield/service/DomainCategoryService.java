package com.fraudshield.service;

import com.fraudshield.domain.DomainCategoryChange;
import com.fraudshield.repo.Repositories.DomainCategoryChangeRepository;
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
 * 13.6: which domains are ads / analytics / tracker / malware. Lookup on the server (13.5 / 13.7: domains an app
 * contacted) and signed delta sync for the phone: GET /config/domains?since=v (same key as the 12.5 blocklist).
 * Seeded from seed/domain-categories.csv; a domain matches its own entry or any parent's ("x.ads.example.com").
 */
@Service
@Order(21)
public class DomainCategoryService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DomainCategoryService.class);
    public static final Set<String> CATEGORIES = Set.of("ads", "analytics", "tracker", "malware");

    public record Entry(String domain, String category) {}
    public record Delta(long version, boolean full, List<Entry> added, List<String> removed, String signature) {}

    private final DomainCategoryChangeRepository changes; // null in plain unit tests
    private final BlocklistSyncService signer;              // null in plain unit tests
    private volatile Map<String, String> current;

    @Autowired
    public DomainCategoryService(DomainCategoryChangeRepository changes, BlocklistSyncService signer) {
        this.changes = changes; this.signer = signer; this.current = Map.copyOf(seed());
    }

    /** Plain unit tests: the seed file only. */
    public DomainCategoryService() { this(null, null); }

    /** "ads", "analytics", "tracker", "malware" or null. */
    public String category(String host) {
        if (host == null) return null;
        String h = host.toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
        Map<String, String> m = current;
        while (true) {
            String c = m.get(h);
            if (c != null) return c;
            int dot = h.indexOf('.');
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return null; // stop at the registrable-looking last two labels
            h = h.substring(dot + 1);
        }
    }

    public int size() { return current.size(); }

    public Delta since(long since) {
        long latest = changes.findTopByOrderByVersionDesc().map(DomainCategoryChange::getVersion).orElse(0L);
        boolean full = since <= 0 || since > latest;
        List<DomainCategoryChange> rows = full ? changes.findAllByOrderByVersionAsc() : changes.findByVersionGreaterThanOrderByVersionAsc(since);
        if (!full && rows.size() > 20_000) { full = true; rows = changes.findAllByOrderByVersionAsc(); }
        Map<String, DomainCategoryChange> last = new LinkedHashMap<>();
        for (var c : rows) { last.remove(c.getDomain()); last.put(c.getDomain(), c); }
        List<Entry> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (var c : last.values()) {
            if (c.getOp().equals("ADD")) added.add(new Entry(c.getDomain(), c.getCategory()));
            else if (!full) removed.add(c.getDomain());
        }
        added.sort(Comparator.comparing(Entry::domain));
        Collections.sort(removed);
        return new Delta(latest, full, added, removed, signer == null ? null : signer.sign(canonical(latest, full, added, removed)));
    }

    /** What is signed; the app rebuilds exactly this text. */
    public static String canonical(long version, boolean full, List<Entry> added, List<String> removed) {
        StringBuilder a = new StringBuilder();
        for (var e : added) { if (a.length() > 0) a.append(','); a.append(e.domain()).append(':').append(e.category()); }
        return "fraudshield-domains-v1\n" + version + "\n" + full + "\n" + a + "\n" + String.join(",", removed);
    }

    static Map<String, String> seed() {
        try (InputStream in = DomainCategoryService.class.getResourceAsStream("/seed/domain-categories.csv")) {
            if (in == null) return Map.of();
            Map<String, String> m = new HashMap<>();
            for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\r?\\n")) {
                if (l.isBlank() || l.startsWith("#") || l.startsWith("domain,")) continue;
                String[] c = l.split(",");
                if (c.length == 2 && CATEGORIES.contains(c[1].strip())) m.put(c[0].strip().toLowerCase(Locale.ROOT), c[1].strip());
            }
            return m;
        } catch (Exception e) {
            throw new IllegalStateException("seed/domain-categories.csv unreadable", e);
        }
    }

    /** Start-up: record seed entries that are new or changed, then serve the table's state. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (changes == null) return;
        Map<String, String> state = new HashMap<>();
        for (var c : changes.findAllByOrderByVersionAsc()) {
            if (c.getOp().equals("ADD")) state.put(c.getDomain(), c.getCategory()); else state.remove(c.getDomain());
        }
        List<DomainCategoryChange> add = new ArrayList<>();
        for (var e : new TreeMap<>(seed()).entrySet())
            if (!e.getValue().equals(state.get(e.getKey()))) { add.add(new DomainCategoryChange(e.getKey(), e.getValue(), "ADD")); state.put(e.getKey(), e.getValue()); }
        if (!add.isEmpty()) { changes.saveAll(add); log.info("Domain categories: {} seeded", add.size()); }
        current = Map.copyOf(state);
    }
}
