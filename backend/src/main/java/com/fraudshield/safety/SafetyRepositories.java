package com.fraudshield.safety;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Phase 15 repositories (nested, picked up via considerNestedRepositories). */
public final class SafetyRepositories {
    private SafetyRepositories() {}

    public interface ScamSignalRepository extends JpaRepository<ScamSignal, Long> {
        List<ScamSignal> findByUserIdAndCreatedAtAfter(Long userId, Instant since);
        @Modifying @Transactional
        @Query("delete from ScamSignal s where s.createdAt < :before")
        int deleteOlderThan(@Param("before") Instant before);
    }

    public interface CrowdItemRepository extends JpaRepository<CrowdItem, Long> {
        Optional<CrowdItem> findByTypeAndValueHash(CrowdItem.Type type, String valueHash);
        /** moderation queue: items that crossed the threshold and nobody has reviewed yet */
        List<CrowdItem> findByStatusInAndReviewedAtIsNullOrderByTrustScoreDesc(Collection<CrowdItem.Status> statuses);
        List<CrowdItem> findByStatusOrderByUpdatedAtDesc(CrowdItem.Status status);
    }

    public interface CrowdReportRepository extends JpaRepository<CrowdReport, Long> {
        boolean existsByItemIdAndReporterId(Long itemId, Long reporterId);
        List<CrowdReport> findByItemId(Long itemId);
        long countByReporterIdAndCreatedAtAfter(Long reporterId, Instant since);
        @Query("select count(r) from CrowdReport r, CrowdItem i where r.itemId = i.id and r.reporterId = :reporterId and i.status = :status")
        long countByReporterAndItemStatus(@Param("reporterId") Long reporterId, @Param("status") CrowdItem.Status status);
    }

    public interface UpiSeenRepository extends JpaRepository<UpiSeen, String> {}
}
