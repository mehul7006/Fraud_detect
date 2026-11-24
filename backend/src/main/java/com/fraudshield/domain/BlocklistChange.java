package com.fraudshield.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 12.5: one ADD / REMOVE of a blocked domain (stored as SHA-256 only). */
@Entity
@Table(name = "blocklist_changes")
public class BlocklistChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long version;
    @Column(name = "domain_hash", nullable = false)
    private String domainHash;
    @Column(nullable = false)
    private String op;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected BlocklistChange() {}
    public BlocklistChange(String domainHash, String op) { this.domainHash = domainHash; this.op = op; }

    public Long getVersion() { return version; }
    public String getDomainHash() { return domainHash; }
    public String getOp() { return op; }
}
