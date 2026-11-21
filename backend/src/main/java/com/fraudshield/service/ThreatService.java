package com.fraudshield.service;

import com.fraudshield.domain.Enums.Severity;
import com.fraudshield.domain.Enums.ThreatType;
import com.fraudshield.domain.ThreatEvent;
import com.fraudshield.dto.Dtos.ThreatDto;
import com.fraudshield.repo.Repositories.ThreatEventRepository;
import com.fraudshield.repo.Repositories.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ThreatService {
    /**
     * "State" threats: while one is OPEN, a second one adds no information (forwarding is either on or off).
     * Also enforced by the unique index uq_threat_open_once (V2), so concurrent reports cannot duplicate.
     */
    static final Set<ThreatType> ONE_OPEN_AT_A_TIME = EnumSet.of(ThreatType.CALL_FORWARDING, ThreatType.SMS_FORWARDING,
            ThreatType.WHATSAPP_TAKEOVER, ThreatType.TELEGRAM_TAKEOVER, ThreatType.CLONE_RISK);

    /** @param created false when an equivalent OPEN threat already existed and was returned instead */
    public record Reported(ThreatDto threat, boolean created) {}

    private final ThreatEventRepository threats;
    private final UserRepository users;

    public ThreatService(ThreatEventRepository threats, UserRepository users) {
        this.threats = threats; this.users = users;
    }

    /** Plain insert, used for per-event threats (links, files, apps). */
    public ThreatEvent record(Long userId, ThreatType type, Severity sev, String title, String detail, String src) {
        return threats.save(new ThreatEvent(userId, type, sev, title, trim(detail, 1000), src));
    }

    /** Records only if the user has no OPEN threat of this type yet (avoids hourly duplicates). */
    public void recordOnce(Long userId, ThreatType type, Severity sev, String title, String detail, String src) {
        report(userId, type, sev, title, detail, src);
    }

    /**
     * Idempotent report for on-device findings. For state threats returns the existing OPEN one instead of
     * creating a duplicate - safe against repeated and concurrent calls.
     */
    public Reported report(Long userId, ThreatType type, Severity sev, String title, String detail, String src) {
        if (!ONE_OPEN_AT_A_TIME.contains(type)) {
            return new Reported(toDto(record(userId, type, sev, title, detail, src)), true);
        }
        Optional<ThreatEvent> existing = findOpen(userId, type);
        if (existing.isPresent()) return new Reported(toDto(existing.get()), false);
        try {
            return new Reported(toDto(record(userId, type, sev, title, detail, src)), true);
        } catch (DataIntegrityViolationException raceLost) {
            // another request inserted the same OPEN threat between our check and insert
            return findOpen(userId, type).map(t -> new Reported(toDto(t), false)).orElseThrow(() -> raceLost);
        }
    }

    private Optional<ThreatEvent> findOpen(Long userId, ThreatType type) {
        return threats.findFirstByUserIdAndTypeAndStatusOrderByCreatedAtDesc(userId, type, "OPEN");
    }

    public com.fraudshield.dto.Dtos.ThreatSummary summary(Long userId) {
        var byType = new java.util.LinkedHashMap<String, Long>();
        for (ThreatType t : ThreatType.values()) byType.put(t.name(), 0L);
        threats.countByType(userId).forEach(r -> byType.put(((ThreatType) r[0]).name(), (Long) r[1]));
        var bySev = new java.util.LinkedHashMap<String, Long>();
        for (Severity v : Severity.values()) bySev.put(v.name(), 0L);
        threats.countBySeverity(userId).forEach(r -> bySev.put(((Severity) r[0]).name(), (Long) r[1]));
        return new com.fraudshield.dto.Dtos.ThreatSummary(threats.countByUserId(userId),
                threats.countByUserIdAndStatus(userId, "OPEN"), byType, bySev);
    }

    public List<ThreatDto> list(Long userId, String status) {
        var rows = status == null ? threats.findByUserIdOrderByCreatedAtDesc(userId)
                : threats.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status);
        return rows.stream().map(ThreatService::toDto).toList();
    }

    @Transactional
    public void resolve(Long userId, Long id) {
        ThreatEvent t = threats.findById(id).filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        t.resolve();
    }

    /** GDPR-style erase: cascades devices + threats via FK. */
    @Transactional
    public void deleteAccount(Long userId) { users.deleteById(userId); }

    static ThreatDto toDto(ThreatEvent t) {
        return new ThreatDto(t.getId(), t.getType().name(), t.getSeverity().name(), t.getTitle(), t.getDetail(),
                t.getSourceApp(), t.getStatus(), t.getCreatedAt().toString());
    }
    private static String trim(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }
}
