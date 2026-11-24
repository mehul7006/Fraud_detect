package com.fraudshield.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 13.6: one ADD / REMOVE of a categorised domain (version = sequence, for delta sync). */
@Entity
@Table(name = "domain_category_changes")
public class DomainCategoryChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long version;
    @Column(nullable = false)
    private String domain;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false)
    private String op;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DomainCategoryChange() {}
    public DomainCategoryChange(String domain, String category, String op) { this.domain = domain; this.category = category; this.op = op; }

    public Long getVersion() { return version; }
    public String getDomain() { return domain; }
    public String getCategory() { return category; }
    public String getOp() { return op; }
}
