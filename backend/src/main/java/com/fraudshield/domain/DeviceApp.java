package com.fraudshield.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "device_apps")
public class DeviceApp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "device_id", nullable = false)
    private String deviceId;
    @Column(name = "package_name", nullable = false)
    private String packageName;
    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen = Instant.now();
    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen = Instant.now();
    @Column(name = "last_score", nullable = false)
    private int lastScore;
    @Column(name = "last_verdict", nullable = false)
    private String lastVerdict = "SAFE";

    protected DeviceApp() {}
    public DeviceApp(Long userId, String deviceId, String packageName) {
        this.userId = userId; this.deviceId = deviceId; this.packageName = packageName;
    }

    public String getPackageName() { return packageName; }
    public String getLastVerdict() { return lastVerdict; }
    public void seen(int score, String verdict) { this.lastSeen = Instant.now(); this.lastScore = score; this.lastVerdict = verdict; }
}
