package com.fraudshield.calls;

import com.fraudshield.calls.CallRepositories.NumberChangeRepository;
import com.fraudshield.calls.CallRepositories.NumberReportRepository;
import com.fraudshield.calls.CallRepositories.NumberReputationRepository;
import com.fraudshield.domain.User;
import com.fraudshield.repo.Repositories.UserRepository;
import com.fraudshield.service.BlocklistSyncService;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 16.1 / 16.2 / 16.4 / 16.5: crowd number reputation.
 * <ul>
 *   <li>One vote per user per number; reporting again only changes that vote's category.</li>
 *   <li>A vote weighs by the reporter's trust (account age): &lt;1 day 0.2, &lt;7 days 0.5, older 1.0.</li>
 *   <li>trust score = sum of vote weights; SUSPICIOUS from {@code threshold} (5.0 = five trusted users).
 *       One user reporting 20 times is one vote; five brand-new accounts are 5 x 0.2 = 1.0.</li>
 *   <li>A user may cast at most {@code dailyLimit} new votes per 24 h (429 after that).</li>
 *   <li>TRAI-protected series (1600 BFSI / government service calls, 140 promotional) cannot be reported.</li>
 * </ul>
 * The raw number is canonicalised and hashed at once and never stored or logged.
 */
@Service
public class NumberReputationService {
    private static final Logger log = LoggerFactory.getLogger(NumberReputationService.class);
    public static final Set<String> CATEGORIES = Set.of("SCAM", "SPAM", "TELEMARKETING");

    public record Report(String status, boolean counted, int reports) {}
    public record RangeEntry(String suffix, String category, int reports, double trust, String status, Instant updatedAt) {}
    public record Range(String prefix, List<RangeEntry> entries) {}
    public record Entry(String hash, String category) {}
    public record Delta(long version, boolean full, List<Entry> added, List<String> removed, String signature) {}

    static final int MAX_DELTA = 50_000;

    private final NumberReputationRepository reputations;
    private final NumberReportRepository reports;
    private final NumberChangeRepository changes;
    private final UserRepository users;
    private final BlocklistSyncService signer;
    private final double threshold;
    private final int dailyLimit;

    public NumberReputationService(NumberReputationRepository reputations, NumberReportRepository reports, NumberChangeRepository changes,
                                   UserRepository users, BlocklistSyncService signer,
                                   @Value("${fraudshield.calls.suspicious-threshold:5.0}") double threshold,
                                   @Value("${fraudshield.calls.daily-report-limit:30}") int dailyLimit) {
        this.reputations = reputations; this.reports = reports; this.changes = changes; this.users = users; this.signer = signer;
        this.threshold = threshold; this.dailyLimit = dailyLimit;
    }

    /** Trust of one reporter's vote. */
    static double weight(User u, Instant now) {
        Duration age = Duration.between(u.getCreatedAt(), now);
        if (age.compareTo(Duration.ofDays(1)) < 0) return 0.2;
        if (age.compareTo(Duration.ofDays(7)) < 0) return 0.5;
        return 1.0;
    }

    @Transactional
    public Report report(Long uid, String rawNumber, String rawCategory) {
        String category = rawCategory == null ? "" : rawCategory.strip().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category must be one of SCAM, SPAM, TELEMARKETING");
        String canonical = PhoneNumbers.canonical(rawNumber);
        if (canonical == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a valid phone number");
        if (NumberRuleService.isProtected(canonical))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "PROTECTED_SERIES: TRAI does not allow 1600 / 140 calls to be tagged or blocked");
        String hash = PhoneNumbers.hash(canonical);
        canonical = null; // nothing below sees the number

        User u = users.findById(uid).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        Instant now = Instant.now();
        double w = weight(u, now);
        Optional<NumberReport> mine = reports.findByUserIdAndNumberHash(uid, hash);
        boolean counted = mine.isEmpty();
        if (counted && reports.countByUserIdAndCreatedAtAfter(uid, now.minus(Duration.ofDays(1))) >= dailyLimit)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily report limit reached");

        reputations.insertIfAbsent(hash, category);
        NumberReputation r = reputations.lockByHash(hash).orElseThrow();
        if (counted) reports.save(new NumberReport(uid, hash, category, w));
        else mine.get().change(category, mine.get().getWeight());
        reports.flush();

        List<NumberReport> votes = reports.findByNumberHash(hash);
        double trust = 0;
        Map<String, Double> byCat = new HashMap<>();
        for (var v : votes) { trust += v.getWeight(); byCat.merge(v.getCategory(), v.getWeight(), Double::sum); }
        // most weight wins; ties go to the more serious category
        String top = byCat.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).thenComparing(e -> -severity(e.getKey())))
                .map(Map.Entry::getKey).orElse(category);
        trust = Math.round(trust * 100) / 100.0;
        boolean wasSuspicious = r.isSuspicious();
        String oldCategory = r.getCategory();
        boolean suspicious = trust >= threshold;
        r.update(top, votes.size(), trust, suspicious);
        if (suspicious && (!wasSuspicious || !oldCategory.equals(top))) changes.save(new NumberChange(hash, top, "ADD"));
        log.info("Number report: counted={} reports={} suspicious={}", counted, votes.size(), suspicious);
        return new Report(suspicious ? "SUSPICIOUS" : "REPORTED", counted, votes.size());
    }

    private static int severity(String c) { return switch (c) { case "SCAM" -> 0; case "SPAM" -> 1; default -> 2; }; }

    /** 16.2: SUSPICIOUS numbers whose hash starts with these 5 hex characters (suffix = the other 59). */
    @Transactional(readOnly = true)
    public Range range(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        if (!p.matches("[0-9a-f]{5}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "prefix must be 5 hex characters");
        List<RangeEntry> l = reputations.findByNumberHashStartingWithAndSuspiciousTrueOrderByNumberHash(p).stream()
                .map(r -> new RangeEntry(r.getNumberHash().substring(5), r.getCategory(), r.getReportCount(), r.getTrustScore(),
                        "SUSPICIOUS", r.getUpdatedAt()))
                .toList();
        return new Range(p, l);
    }

    /** Admin: a number was wrongly flagged - forget its reports and tell phones to remove it. */
    @Transactional
    public boolean clear(String hash) {
        if (!PhoneNumbers.isHash(hash)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a number hash");
        Optional<NumberReputation> r = reputations.findById(hash);
        if (r.isEmpty()) return false;
        boolean listed = r.get().isSuspicious();
        reports.deleteAll(reports.findByNumberHash(hash));
        reputations.delete(r.get());
        if (listed) changes.save(new NumberChange(hash, null, "REMOVE"));
        return true;
    }

    /** 16.4: signed list of SUSPICIOUS number hashes, as changes since the phone's version. */
    @Transactional(readOnly = true)
    public Delta since(long since) {
        long latest = changes.findTopByOrderByVersionDesc().map(NumberChange::getVersion).orElse(0L);
        boolean full = since <= 0 || since > latest;
        List<NumberChange> rows = full ? changes.findAllByOrderByVersionAsc() : changes.findByVersionGreaterThanOrderByVersionAsc(since);
        if (!full && rows.size() > MAX_DELTA) { full = true; rows = changes.findAllByOrderByVersionAsc(); }
        Map<String, NumberChange> last = new LinkedHashMap<>();
        for (var c : rows) { last.remove(c.getNumberHash()); last.put(c.getNumberHash(), c); }
        List<Entry> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (var c : last.values()) {
            if (c.getOp().equals("ADD")) added.add(new Entry(c.getNumberHash(), c.getCategory()));
            else if (!full) removed.add(c.getNumberHash());
        }
        added.sort(Comparator.comparing(Entry::hash));
        Collections.sort(removed);
        return new Delta(latest, full, added, removed, signer.sign(canonical(latest, full, added, removed)));
    }

    /** What is signed; the app rebuilds exactly this text. */
    public static String canonical(long version, boolean full, List<Entry> added, List<String> removed) {
        StringBuilder a = new StringBuilder();
        for (var e : added) { if (a.length() > 0) a.append(','); a.append(e.hash()).append(':').append(e.category()); }
        return "fraudshield-numbers-v1\n" + version + "\n" + full + "\n" + a + "\n" + String.join(",", removed);
    }
}
