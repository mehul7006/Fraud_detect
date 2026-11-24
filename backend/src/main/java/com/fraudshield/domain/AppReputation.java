package com.fraudshield.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_reputation")
public class AppReputation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "package_name", nullable = false, unique = true)
    private String packageName;
    private String label;
    @Column(nullable = false)
    private String verdict; // GOOD | BAD
    @Column(nullable = false)
    private String source;
    @Column(name = "cert_sha256")
    private String certSha256; // 6.2: official signing cert(s), normalised hex, comma separated; null = not known yet
    private String category;  // 13.1 what the app is for (CategoryRules)
    private String trackers;  // 13.1 tracker SDK names known to be inside, comma separated (null = not known)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AppReputation() {}
    public AppReputation(String packageName, String label, String verdict, String source) {
        this.packageName = packageName; this.label = label; this.verdict = verdict; this.source = source;
    }

    public Long getId() { return id; }
    public String getPackageName() { return packageName; }
    public String getLabel() { return label; }
    public String getVerdict() { return verdict; }
    public String getSource() { return source; }
    public String getCertSha256() { return certSha256; }
    public String getCategory() { return category; }
    public String getTrackers() { return trackers; }
    public void setCategory(String category) { this.category = category; }
    public void setTrackers(String trackers) { this.trackers = trackers; }
    public void setCertSha256(String certSha256) { this.certSha256 = certSha256; this.updatedAt = Instant.now(); }
    public void update(String label, String verdict, String source) {
        this.label = label; this.verdict = verdict; this.source = source; this.updatedAt = Instant.now();
    }
}
