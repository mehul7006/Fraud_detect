package com.fraudshield.safety;

import jakarta.persistence.*;
import java.time.Instant;

/** 15.7: one user's vote on one item (unique per item + reporter), with the trust weight it had when cast. */
@Entity
@Table(name = "crowd_reports")
public class CrowdReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "item_id", nullable = false)
    private Long itemId;
    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;
    @Column(nullable = false)
    private double weight;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CrowdReport() {}
    public CrowdReport(Long itemId, Long reporterId, double weight) { this.itemId = itemId; this.reporterId = reporterId; this.weight = weight; }

    public Long getItemId() { return itemId; }
    public Long getReporterId() { return reporterId; }
    public double getWeight() { return weight; }
    public Instant getCreatedAt() { return createdAt; }
}
