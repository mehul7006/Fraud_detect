package com.fraudshield.repo;

import com.fraudshield.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

public final class Repositories {
    private Repositories() {}

    @Repository
    public interface UserRepository extends JpaRepository<User, Long> {
        Optional<User> findByEmail(String email);
    }

    @Repository
    public interface RefreshTokenRepository extends JpaRepository<com.fraudshield.domain.RefreshToken, Long> {
        Optional<com.fraudshield.domain.RefreshToken> findByTokenHash(String tokenHash);
        List<com.fraudshield.domain.RefreshToken> findByFamily(String family);
        List<com.fraudshield.domain.RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
    }

    @Repository
    public interface DeviceRepository extends JpaRepository<Device, Long> {
        Optional<Device> findByUserIdAndDeviceId(Long userId, String deviceId);
        List<Device> findByUserId(Long userId);
        void deleteByUserId(Long userId);
    }

    @Repository
    public interface ThreatEventRepository extends JpaRepository<ThreatEvent, Long> {
        List<ThreatEvent> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
        List<ThreatEvent> findByUserIdOrderByCreatedAtDesc(Long userId);
        List<ThreatEvent> findByUserIdAndTypeInAndCreatedAtAfter(Long userId, java.util.Collection<com.fraudshield.domain.Enums.ThreatType> types,
                java.time.Instant after);
        @org.springframework.data.jpa.repository.Query("select t.type, count(t) from ThreatEvent t where t.userId = :uid group by t.type")
        List<Object[]> countByType(@org.springframework.data.repository.query.Param("uid") Long uid);
        @org.springframework.data.jpa.repository.Query("select t.severity, count(t) from ThreatEvent t where t.userId = :uid group by t.severity")
        List<Object[]> countBySeverity(@org.springframework.data.repository.query.Param("uid") Long uid);
        long countByUserIdAndStatus(Long userId, String status);
        long countByUserId(Long userId);
        boolean existsByUserIdAndTypeAndStatus(Long userId, com.fraudshield.domain.Enums.ThreatType type, String status);
        Optional<ThreatEvent> findFirstByUserIdAndTypeAndStatusOrderByCreatedAtDesc(Long userId, com.fraudshield.domain.Enums.ThreatType type, String status);
    }

    @Repository
    public interface CarrierPatternRepository extends JpaRepository<CarrierPattern, Long> {
        List<CarrierPattern> findByEnabledTrueOrderByPriorityAscIdAsc();
    }

    @Repository
    public interface BlocklistRepository extends JpaRepository<Blocklist, Long> {
        Optional<Blocklist> findByKindAndValue(String kind, String value);
    }

    @Repository
    public interface BlocklistChangeRepository extends JpaRepository<com.fraudshield.domain.BlocklistChange, Long> {
        Optional<com.fraudshield.domain.BlocklistChange> findTopByOrderByVersionDesc();
        List<com.fraudshield.domain.BlocklistChange> findAllByOrderByVersionAsc();
        List<com.fraudshield.domain.BlocklistChange> findByVersionGreaterThanOrderByVersionAsc(long version);
    }

    @Repository
    public interface OfficialAppRepository extends JpaRepository<com.fraudshield.domain.OfficialApp, Long> {
        Optional<com.fraudshield.domain.OfficialApp> findByPackageName(String packageName);
    }

    @Repository
    public interface DomainCategoryChangeRepository extends JpaRepository<com.fraudshield.domain.DomainCategoryChange, Long> {
        Optional<com.fraudshield.domain.DomainCategoryChange> findTopByOrderByVersionDesc();
        List<com.fraudshield.domain.DomainCategoryChange> findAllByOrderByVersionAsc();
        List<com.fraudshield.domain.DomainCategoryChange> findByVersionGreaterThanOrderByVersionAsc(long version);
    }

    @Repository
    public interface BrandRepository extends JpaRepository<com.fraudshield.domain.BrandEntry, Long> {
        Optional<com.fraudshield.domain.BrandEntry> findByName(String name);
    }

    @Repository
    public interface AppReputationRepository extends JpaRepository<AppReputation, Long> {
        Optional<AppReputation> findByPackageName(String packageName);
        List<AppReputation> findByVerdictOrderByPackageName(String verdict);
    }

    @Repository
    public interface AppLabelRepository extends JpaRepository<com.fraudshield.domain.AppLabel, Long> {
        Optional<com.fraudshield.domain.AppLabel> findByUserIdAndPackageName(Long userId, String packageName);
        List<com.fraudshield.domain.AppLabel> findAllByOrderByIdAsc();
    }

    @Repository
    public interface DeviceAppRepository extends JpaRepository<DeviceApp, Long> {
        List<DeviceApp> findByUserIdAndDeviceId(Long userId, String deviceId);
    }

    @Repository
    public interface UrlFeedbackRepository extends JpaRepository<UrlFeedback, Long> {
        boolean existsByUserIdAndHostAndStatus(Long userId, String host, String status);
        List<UrlFeedback> findByStatusOrderByCreatedAtAsc(String status);
    }
}
