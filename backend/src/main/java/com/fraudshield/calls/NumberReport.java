package com.fraudshield.calls;

import jakarta.persistence.*;
import java.time.Instant;

/** Phase 16 (V17__call_guard.sql): numbers only as SHA-256 hashes. */
@Entity
@Table(name = "number_reports")
public class NumberReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "number_hash", nullable = false)
    private String numberHash;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false)
    private double weight;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected NumberReport() {}
    public NumberReport(Long userId, String numberHash, String category, double weight) {
        this.userId = userId; this.numberHash = numberHash; this.category = category; this.weight = weight;
    }
    public Long getUserId() { return userId; }
    public String getNumberHash() { return numberHash; }
    public String getCategory() { return category; }
    public double getWeight() { return weight; }
    void change(String category, double weight) { this.category = category; this.weight = weight; }
}
