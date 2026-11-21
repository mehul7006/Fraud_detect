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
    public interface DeviceRepository extends JpaRepository<Device, Long> {
        Optional<Device> findByUserIdAndDeviceId(Long userId, String deviceId);
        List<Device> findByUserId(Long userId);
        void deleteByUserId(Long userId);
    }

    @Repository
    public interface ThreatEventRepository extends JpaRepository<ThreatEvent, Long> {
        List<ThreatEvent> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
        List<ThreatEvent> findByUserIdOrderByCreatedAtDesc(Long userId);
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
    public interface UrlFeedbackRepository extends JpaRepository<UrlFeedback, Long> {
        boolean existsByUserIdAndHostAndStatus(Long userId, String host, String status);
        List<UrlFeedback> findByStatusOrderByCreatedAtAsc(String status);
    }
}
