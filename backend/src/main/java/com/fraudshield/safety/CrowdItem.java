package com.fraudshield.safety;

import jakarta.persistence.*;
import java.time.Instant;

/** 15.7: one reported item (a website or a UPI ID), known only by its hash. Phone numbers live in the Phase 16 store. */
@Entity
@Table(name = "crowd_items")
public class CrowdItem {
    public enum Type { LINK, UPI }
    public enum Status { UNVERIFIED, SUSPICIOUS, CONFIRMED, CLEARED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "item_type", nullable = false, length = 16)
    private Type type;
    @Column(name = "value_hash", nullable = false, length = 64)
    private String valueHash;
    @Column(name = "link_host", length = 253)
    private String linkHost;
    @Column(name = "report_count", nullable = false)
    private int reportCount;
    @Column(name = "trust_score", nullable = false)
    private double trustScore;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private Status status = Status.UNVERIFIED;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "first_at", nullable = false)
    private Instant firstAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CrowdItem() {}
    public CrowdItem(Type type, String valueHash, String linkHost) { this.type = type; this.valueHash = valueHash; this.linkHost = linkHost; }

    public Long getId() { return id; }
    public Type getType() { return type; }
    public String getValueHash() { return valueHash; }
    public String getLinkHost() { return linkHost; }
    public void setLinkHost(String h) { this.linkHost = h; }
    public int getReportCount() { return reportCount; }
    public double getTrustScore() { return trustScore; }
    public Status getStatus() { return status; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getFirstAt() { return firstAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    void tally(int count, double score, Status status) {
        this.reportCount = count; this.trustScore = score; this.status = status; this.updatedAt = Instant.now();
    }
    void review(Status s) { this.status = s; this.reviewedAt = Instant.now(); this.updatedAt = this.reviewedAt; }
}
