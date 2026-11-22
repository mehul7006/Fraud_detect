package com.fraudshield.domain;

import com.fraudshield.dto.Dtos.AppInfo;
import jakarta.persistence.*;
import java.time.Instant;

/** 6.1: one user's decision about one flagged app plus the signals it had at that moment. */
@Entity
@Table(name = "app_labels")
public class AppLabel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "package_name", nullable = false) private String packageName;
    @Column(nullable = false) private String label;
    @Column(name = "notif_per_hour", nullable = false) private int notifPerHour;
    @Column(nullable = false) private boolean overlay;
    @Column(nullable = false) private boolean sms;
    @Column(nullable = false) private boolean accessibility;
    @Column(nullable = false) private boolean listener;
    @Column(name = "full_screen", nullable = false) private boolean fullScreen;
    @Column(name = "self_launch", nullable = false) private int selfLaunch;
    @Column(name = "score_shown", nullable = false) private int scoreShown;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected AppLabel() {}
    public AppLabel(Long userId, String packageName) { this.userId = userId; this.packageName = packageName; }

    public void set(String label, AppInfo a, int scoreShown) {
        this.label = label;
        this.notifPerHour = Math.max(0, Math.min(10_000, a.notifPerHour()));
        this.overlay = a.hasOverlayPermission(); this.sms = a.hasSmsPermission(); this.accessibility = a.hasAccessibility();
        this.listener = a.hasNotificationListener(); this.fullScreen = a.hasFullScreenIntent();
        this.selfLaunch = Math.max(0, Math.min(10_000, a.selfLaunchCount()));
        this.scoreShown = Math.max(0, Math.min(100, scoreShown));
        this.createdAt = Instant.now();
    }

    public String getPackageName() { return packageName; }
    public String getLabel() { return label; }
    public int getNotifPerHour() { return notifPerHour; }
    public boolean isOverlay() { return overlay; }
    public boolean isSms() { return sms; }
    public boolean isAccessibility() { return accessibility; }
    public boolean isListener() { return listener; }
    public boolean isFullScreen() { return fullScreen; }
    public int getSelfLaunch() { return selfLaunch; }
}
