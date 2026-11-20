package com.fraudshield.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "devices", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "device_id"}))
public class Device {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "device_id", nullable = false)
    private String deviceId;
    private String model;
    @Column(name = "os_version")
    private String osVersion;
    @Column(name = "sim_hash")
    private String simHash;
    private String fingerprint;
    @Column(name = "clone_risk_score", nullable = false)
    private int cloneRiskScore;
    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen = Instant.now();

    protected Device() {}
    public Device(Long userId, String deviceId) { this.userId = userId; this.deviceId = deviceId; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getDeviceId() { return deviceId; }
    public String getModel() { return model; }
    public void setModel(String m) { this.model = m; }
    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String v) { this.osVersion = v; }
    public String getSimHash() { return simHash; }
    public void setSimHash(String s) { this.simHash = s; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String f) { this.fingerprint = f; }
    public int getCloneRiskScore() { return cloneRiskScore; }
    public void setCloneRiskScore(int s) { this.cloneRiskScore = s; }
    public Instant getLastSeen() { return lastSeen; }
    public void touch() { this.lastSeen = Instant.now(); }
}
