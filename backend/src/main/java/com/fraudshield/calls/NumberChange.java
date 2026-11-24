package com.fraudshield.calls;

import jakarta.persistence.*;
import java.time.Instant;

/** Phase 16 (V17__call_guard.sql): numbers only as SHA-256 hashes. */
@Entity
@Table(name = "number_changes")
public class NumberChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long version;
    @Column(name = "number_hash", nullable = false)
    private String numberHash;
    private String category;
    @Column(nullable = false)
    private String op;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected NumberChange() {}
    public NumberChange(String numberHash, String category, String op) { this.numberHash = numberHash; this.category = category; this.op = op; }
    public Long getVersion() { return version; }
    public String getNumberHash() { return numberHash; }
    public String getCategory() { return category; }
    public String getOp() { return op; }
}
