package com.fraudshield.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 10.1: one row of the brands table. Lists are '|' separated, as in seed/brands.csv. */
@Entity
@Table(name = "brands")
public class BrandEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;
    @Column(nullable = false)
    private String category;
    @Column(name = "official_domains", nullable = false)
    private String officialDomains;
    @Column(nullable = false)
    private String keywords;
    @Column(name = "favicon_hash")
    private String faviconHash;
    @Column(nullable = false)
    private String source;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected BrandEntry() {}
    public BrandEntry(String name, String category, String officialDomains, String keywords, String source) {
        this.name = name; this.category = category; this.officialDomains = officialDomains; this.keywords = keywords;
        this.source = source;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getOfficialDomains() { return officialDomains; }
    public String getKeywords() { return keywords; }
    public String getFaviconHash() { return faviconHash; }
    public void setFaviconHash(String sha256Hex) { this.faviconHash = sha256Hex; this.updatedAt = Instant.now(); }
    public String getSource() { return source; }
}
