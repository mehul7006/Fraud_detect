package com.fraudshield.calls;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Phase 16 repositories. */
public final class CallRepositories {
    private CallRepositories() {}

    public interface NumberReputationRepository extends JpaRepository<NumberReputation, String> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select r from NumberReputation r where r.numberHash = ?1")
        Optional<NumberReputation> lockByHash(String hash);
        /** First report of a number: create the row without racing a parallel first report. */
        @org.springframework.data.jpa.repository.Modifying
        @Query(value = "insert into number_reputation (number_hash, category) values (?1, ?2) on conflict do nothing", nativeQuery = true)
        int insertIfAbsent(String hash, String category);
        List<NumberReputation> findByNumberHashStartingWithAndSuspiciousTrueOrderByNumberHash(String prefix);
    }

    public interface NumberReportRepository extends JpaRepository<NumberReport, Long> {
        Optional<NumberReport> findByUserIdAndNumberHash(Long userId, String numberHash);
        List<NumberReport> findByNumberHash(String numberHash);
        long countByUserIdAndCreatedAtAfter(Long userId, Instant after);
    }

    public interface NumberChangeRepository extends JpaRepository<NumberChange, Long> {
        Optional<NumberChange> findTopByOrderByVersionDesc();
        List<NumberChange> findAllByOrderByVersionAsc();
        List<NumberChange> findByVersionGreaterThanOrderByVersionAsc(long version);
    }
}
