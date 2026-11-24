package com.fraudshield.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 13.13: one official app of a brand. certSha256 null = certificate not verified yet. */
@Entity
@Table(name = "official_apps")
public class OfficialApp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String brand;
    @Column(nullable = false)
    private String category;
    @Column(name = "package_name", nullable = false, unique = true)
    private String packageName;
    @Column(name = "play_title")
    private String playTitle;
    @Column(name = "cert_sha256")
    private String certSha256;
    @Column(nullable = false)
    private String source;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected OfficialApp() {}
    public OfficialApp(String brand, String category, String packageName, String playTitle, String certSha256, String source) {
        this.brand = brand; this.category = category; this.packageName = packageName; this.playTitle = playTitle;
        this.certSha256 = certSha256; this.source = source;
    }

    public String getBrand() { return brand; }
    public String getCategory() { return category; }
    public String getPackageName() { return packageName; }
    public String getPlayTitle() { return playTitle; }
    public String getCertSha256() { return certSha256; }
    public String getSource() { return source; }
}
