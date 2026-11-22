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
    public void update(String label, String verdict, String source) {
        this.label = label; this.verdict = verdict; this.source = source; this.updatedAt = Instant.now();
    }
}
