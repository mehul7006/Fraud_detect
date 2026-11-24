package com.fraudshield.domain;

import com.fraudshield.domain.Enums.Severity;
import com.fraudshield.domain.Enums.ThreatType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "threat_events")
public class ThreatEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ThreatType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Severity severity;
    @Column(nullable = false)
    private String title;
    private String detail;
    @Column(name = "explanation_en")
    private String explanationEn;
    @Column(name = "explanation_hi")
    private String explanationHi;
    @Column(name = "source_app")
    private String sourceApp;
    @Column(nullable = false)
    private String status = "OPEN";
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ThreatEvent() {}
    public ThreatEvent(Long userId, ThreatType type, Severity severity, String title, String detail, String sourceApp) {
        this.userId = userId; this.type = type; this.severity = severity;
        this.title = title; this.detail = detail; this.sourceApp = sourceApp;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public ThreatType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public String getExplanationEn() { return explanationEn; }
    public String getExplanationHi() { return explanationHi; }
    public void setExplanation(String en, String hi) { this.explanationEn = en; this.explanationHi = hi; }
    public String getSourceApp() { return sourceApp; }
    public String getStatus() { return status; }
    public void resolve() { this.status = "RESOLVED"; }
    public Instant getCreatedAt() { return createdAt; }
}
