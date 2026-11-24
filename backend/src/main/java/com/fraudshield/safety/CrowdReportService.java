package com.fraudshield.safety;

import com.fraudshield.domain.User;
import com.fraudshield.repo.Repositories.UserRepository;
import com.fraudshield.safety.CrowdItem.Status;
import com.fraudshield.safety.CrowdItem.Type;
import com.fraudshield.safety.SafetyRepositories.CrowdItemRepository;
import com.fraudshield.safety.SafetyRepositories.CrowdReportRepository;
import com.fraudshield.service.BlocklistService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 15.7: crowd reports for websites, UPI IDs and customer-care numbers.
 * <ul>
 *   <li><b>hashed</b>: only {@code sha256(normalised value)} is stored (a website's host is kept as well, because a
 *       confirmed fake site must be blocked by name; UPI IDs and numbers never are)</li>
 *   <li><b>de-duplicated</b>: one vote per user per item - reporting the same thing 20 times counts once</li>
 *   <li><b>trust-weighted</b>: an established account (older than {@code trusted-min-age-days}) weighs 1.0, a brand-new
 *       one {@code new-account-weight}; every earlier report of the user that a moderator CLEARED (false report)
 *       costs {@code false-report-penalty}</li>
 *   <li><b>threshold</b>: trust score &ge; {@code suspicious-score} (5 trusted reports) -> SUSPICIOUS</li>
 *   <li><b>moderation queue</b>: SUSPICIOUS items (and busy UNVERIFIED ones) wait for an admin, who CONFIRMs
 *       (a confirmed website goes on the blocklist) or CLEARs them (false reports lower the reporters' trust)</li>
 * </ul>
 */
@Service
public class CrowdReportService {
    public record Outcome(boolean counted, Status status) {}
    public record ItemView(Long id, Type type, String valueHash, String linkHost, int reportCount, double trustScore,
                           Status status, Instant firstAt, Instant updatedAt) {
        static ItemView of(CrowdItem i) {
            return new ItemView(i.getId(), i.getType(), i.getValueHash(), i.getLinkHost(), i.getReportCount(),
                    Math.round(i.getTrustScore() * 100) / 100.0, i.getStatus(), i.getFirstAt(), i.getUpdatedAt());
        }
    }
    public enum Decision { CONFIRM, CLEAR }

    private final CrowdItemRepository items;
    private final CrowdReportRepository reports;
    private final UserRepository users;
    private final BlocklistService blocklist;
    private final Clock clock;
    final double suspiciousScore;
    final double newAccountWeight;
    final double falseReportPenalty;
    final Duration trustedMinAge;
    final int dailyCap;
    final int queueMinReports;

    @Autowired
    public CrowdReportService(CrowdItemRepository items, CrowdReportRepository reports, UserRepository users, BlocklistService blocklist,
                              @Value("${fraudshield.reports.suspicious-score:5.0}") double suspiciousScore,
                              @Value("${fraudshield.reports.new-account-weight:0.2}") double newAccountWeight,
                              @Value("${fraudshield.reports.false-report-penalty:0.5}") double falseReportPenalty,
                              @Value("${fraudshield.reports.trusted-min-age-days:7}") long trustedMinAgeDays,
                              @Value("${fraudshield.reports.daily-cap:30}") int dailyCap,
                              @Value("${fraudshield.reports.queue-min-reports:3}") int queueMinReports) {
        this(items, reports, users, blocklist, suspiciousScore, newAccountWeight, falseReportPenalty,
                Duration.ofDays(trustedMinAgeDays), dailyCap, queueMinReports, Clock.systemUTC());
    }

    CrowdReportService(CrowdItemRepository items, CrowdReportRepository reports, UserRepository users, BlocklistService blocklist,
                       double suspiciousScore, double newAccountWeight, double falseReportPenalty, Duration trustedMinAge,
                       int dailyCap, int queueMinReports, Clock clock) {
        this.items = items; this.reports = reports; this.users = users; this.blocklist = blocklist;
        this.suspiciousScore = suspiciousScore; this.newAccountWeight = newAccountWeight;
        this.falseReportPenalty = falseReportPenalty; this.trustedMinAge = trustedMinAge;
        this.dailyCap = dailyCap; this.queueMinReports = queueMinReports; this.clock = clock;
    }

    /**
     * Records one report. Give either the plain {@code value} (hashed here, never stored as-is - except a website's
     * host) or the {@code valueHash} computed on the phone with {@link SafetyValues}.
     */
    public Outcome report(Long userId, Type type, String value, String valueHash) {
        String hash;
        String host = null;
        if (value != null && !value.isBlank()) {
            String norm = SafetyValues.normalise(type, value);
            if (norm.isBlank() || norm.length() > 253) throw bad("value is not a valid " + type);
            hash = SafetyValues.sha256(norm);
            if (type == Type.LINK) host = norm;
        } else if (SafetyValues.isHash(valueHash)) {
            hash = valueHash;
        } else {
            throw bad("give value or valueHash (64 lowercase hex characters)");
        }
        Instant now = Instant.now(clock);
        if (reports.countByReporterIdAndCreatedAtAfter(userId, now.minus(Duration.ofDays(1))) >= dailyCap)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "daily report limit reached");

        CrowdItem item = findOrCreate(type, hash, host);
        if (reports.existsByItemIdAndReporterId(item.getId(), userId)) return new Outcome(false, item.getStatus());
        try {
            reports.saveAndFlush(new CrowdReport(item.getId(), userId, weightOf(userId)));
        } catch (DataIntegrityViolationException sameVoteRace) {
            return new Outcome(false, item.getStatus());
        }
        return new Outcome(true, retally(item.getId()).getStatus());
    }

    /** Status of an item (UNVERIFIED when nobody reported it). */
    public Status status(Type type, String hash) {
        return items.findByTypeAndValueHash(type, hash).map(CrowdItem::getStatus).orElse(Status.UNVERIFIED);
    }

    public Optional<CrowdItem> find(Type type, String hash) { return items.findByTypeAndValueHash(type, hash); }

    /** Trust weight of a reporter right now. */
    double weightOf(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Instant created = u.getCreatedAt() == null ? Instant.now(clock) : u.getCreatedAt();
        double w = created.isBefore(Instant.now(clock).minus(trustedMinAge)) ? 1.0 : newAccountWeight;
        w -= falseReportPenalty * reports.countByReporterAndItemStatus(userId, Status.CLEARED);
        return Math.max(0, w);
    }

    private CrowdItem findOrCreate(Type type, String hash, String host) {
        Optional<CrowdItem> found = items.findByTypeAndValueHash(type, hash);
        if (found.isPresent()) {
            CrowdItem i = found.get();
            if (host != null && i.getLinkHost() == null) { i.setLinkHost(host); return items.save(i); }
            return i;
        }
        try {
            return items.saveAndFlush(new CrowdItem(type, hash, host));
        } catch (DataIntegrityViolationException createdMeanwhile) {
            return items.findByTypeAndValueHash(type, hash).orElseThrow(() -> createdMeanwhile);
        }
    }

    /** Recomputes count + trust score from all votes (safe to repeat); reviewed items keep the moderator's decision. */
    CrowdItem retally(Long itemId) {
        CrowdItem item = items.findById(itemId).orElseThrow();
        List<CrowdReport> votes = reports.findByItemId(itemId);
        double score = votes.stream().mapToDouble(CrowdReport::getWeight).sum();
        Status s = item.getReviewedAt() != null ? item.getStatus()
                : score >= suspiciousScore - 1e-9 ? Status.SUSPICIOUS : Status.UNVERIFIED;
        item.tally(votes.size(), score, s);
        return items.save(item);
    }

    // ---- moderation (ADMIN)

    public List<ItemView> queue() {
        return items.findByStatusInAndReviewedAtIsNullOrderByTrustScoreDesc(EnumSet.of(Status.SUSPICIOUS, Status.UNVERIFIED)).stream()
                .filter(i -> i.getStatus() == Status.SUSPICIOUS || i.getReportCount() >= queueMinReports)
                .map(ItemView::of).toList();
    }

    public ItemView review(Long itemId, Decision d) {
        CrowdItem item = items.findById(itemId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        item.review(d == Decision.CONFIRM ? Status.CONFIRMED : Status.CLEARED);
        item = items.save(item);
        if (d == Decision.CONFIRM && item.getType() == Type.LINK && item.getLinkHost() != null)
            blocklist.addDomainIfMissing(item.getLinkHost(), "crowd reports, confirmed by moderator");
        return ItemView.of(item);
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
