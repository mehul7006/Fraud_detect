package com.fraudshield.calls;

import jakarta.persistence.*;
import java.time.Instant;

/** Phase 16 (V17__call_guard.sql): numbers only as SHA-256 hashes. */
@Entity
@Table(name = "number_reputation")
public class NumberReputation {
    @Id @Column(name = "number_hash")
    private String numberHash;
    @Column(nullable = false)
    private String category;
    @Column(name = "report_count", nullable = false)
    private int reportCount;
    @Column(name = "trust_score", nullable = false)
    private double trustScore;
    @Column(nullable = false)
    private boolean suspicious;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected NumberReputation() {}
    public NumberReputation(String numberHash, String category) { this.numberHash = numberHash; this.category = category; }

    public String getNumberHash() { return numberHash; }
    public String getCategory() { return category; }
    public int getReportCount() { return reportCount; }
    public double getTrustScore() { return trustScore; }
    public boolean isSuspicious() { return suspicious; }
    public Instant getUpdatedAt() { return updatedAt; }
    void update(String category, int count, double trust, boolean suspicious) {
        this.category = category; this.reportCount = count; this.trustScore = trust; this.suspicious = suspicious; this.updatedAt = Instant.now();
    }
}
