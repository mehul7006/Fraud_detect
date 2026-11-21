package com.fraudshield.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** A user report that a URL verdict was wrong. Stores the host only, never the path or query. */
@Entity
@Table(name = "url_feedback")
public class UrlFeedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String host;
    @Column(nullable = false)
    private String kind;
    private String note;
    @Column(nullable = false)
    private String status = "OPEN";
    private String resolution;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UrlFeedback() {}
    public UrlFeedback(Long userId, String host, String kind, String note) {
        this.userId = userId; this.host = host; this.kind = kind; this.note = note;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getHost() { return host; }
    public String getKind() { return kind; }
    public String getNote() { return note; }
    public String getStatus() { return status; }
    public String getResolution() { return resolution; }
    public Instant getCreatedAt() { return createdAt; }
    public void close(String resolution) { this.status = "CLOSED"; this.resolution = resolution; }
}
