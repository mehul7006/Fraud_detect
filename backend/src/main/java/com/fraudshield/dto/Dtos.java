package com.fraudshield.dto;

import com.fraudshield.domain.Enums.Verdict;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    public record AuthRequest(@Email @NotBlank String email, @Size(min = 8, max = 72) String password) {}
    /** token = short-lived access token (Bearer); refreshToken = use once at POST /auth/refresh; expiresIn = seconds. */
    public record TokenResponse(String token, String refreshToken, long expiresIn) {}
    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}

    public record DeviceRequest(@NotBlank String deviceId, String model, String osVersion, String simHash,
                                String fingerprint, boolean rooted, boolean emulator, boolean playProtectOn,
                                List<String> remoteAppsInstalled,
                                @Size(max = 512) String pushToken) { // 7.3: FCM token, optional
    }
    public record DeviceResponse(String deviceId, int cloneRiskScore, List<String> reasons) {}

    public record UrlScanRequest(@NotBlank @Size(max = 2048) String url, String sourceApp) {}
    public record ScanResponse(Verdict verdict, int score, List<String> reasons) {}

    public record FileScanRequest(@NotBlank @Size(min = 64, max = 64) String sha256, @NotBlank String fileName,
                                  long sizeBytes, String mimeType, String sourceApp, String headerHex,
                                  @Size(max = 10) List<String> flags) {
        /** Older clients send no content flags. */
        public FileScanRequest(String sha256, String fileName, long sizeBytes, String mimeType, String sourceApp, String headerHex) {
            this(sha256, fileName, sizeBytes, mimeType, sourceApp, headerHex, null);
        }
    }
    public record FileScanResponse(Verdict verdict, int score, List<String> reasons, String action) {}

    public record AppInfo(@NotBlank String packageName, String label, int notifPerHour,
                          boolean hasOverlayPermission, boolean hasSmsPermission,
                          boolean hasAccessibility, boolean hasNotificationListener,
                          boolean hasFullScreenIntent, boolean isSystem, int selfLaunchCount,
                          @Size(max = 400) String certSha256, @Size(max = 200) String installer) {
        /** 6.2: certSha256 = SHA-256 of the app's signing certificate(s), hex, comma separated; installer = the
         *  package that installed it (com.android.vending = Google Play). Both optional (older clients). */
        public AppInfo(String packageName, String label, int notifPerHour, boolean hasOverlayPermission,
                       boolean hasSmsPermission, boolean hasAccessibility, boolean hasNotificationListener,
                       boolean hasFullScreenIntent, boolean isSystem, int selfLaunchCount) {
            this(packageName, label, notifPerHour, hasOverlayPermission, hasSmsPermission, hasAccessibility,
                 hasNotificationListener, hasFullScreenIntent, isSystem, selfLaunchCount, null, null);
        }
        /** Older clients (before 6.6) send no selfLaunchCount. */
        public AppInfo(String packageName, String label, int notifPerHour, boolean hasOverlayPermission,
                       boolean hasSmsPermission, boolean hasAccessibility, boolean hasNotificationListener,
                       boolean hasFullScreenIntent, boolean isSystem) {
            this(packageName, label, notifPerHour, hasOverlayPermission, hasSmsPermission, hasAccessibility,
                 hasNotificationListener, hasFullScreenIntent, isSystem, 0);
        }
    }
    /** deviceId (6.3) is optional: without it no "new since last scan" tracking happens. */
    public record AppsScanRequest(@Valid @NotNull List<AppInfo> apps, @Size(max = 100) String deviceId) {
        public AppsScanRequest(List<AppInfo> apps) { this(apps, null); }
    }
    public record AppResult(String packageName, int adAbuseScore, Verdict verdict, List<String> reasons,
                            Boolean newSinceLastScan) {
        public AppResult(String packageName, int adAbuseScore, Verdict verdict, List<String> reasons) {
            this(packageName, adAbuseScore, verdict, reasons, null);
        }
    }
    public record AppReputationRequest(@NotBlank @Size(max = 200) String packageName, @Size(max = 120) String label,
                                       @NotBlank @Pattern(regexp = "GOOD|BAD") String verdict,
                                       @Size(max = 400) String certSha256) {
        public AppReputationRequest(String packageName, String label, String verdict) { this(packageName, label, verdict, null); }
    }
    public record AppReputationEntry(String packageName, String label, String verdict, String source, String certSha256) {}
    public record AppsScanResponse(List<AppResult> results) {}
    /** 6.1 (opt-in): the user's decision about a flagged app. Uninstall = ABUSIVE, Ignore = BENIGN. */
    public record AppLabelRequest(@Valid @NotNull AppInfo app, @NotBlank @Pattern(regexp = "ABUSIVE|BENIGN") String label,
                                  int scoreShown) {}

    public record ThreatDto(Long id, String type, String severity, String title, String detail,
                            String sourceApp, String status, String createdAt) {}

    public record CarrierRule(String country, String carrier, String kind, String regex, int priority) {}
    public record CarrierConfigResponse(String version, List<CarrierRule> rules) {}

    public record BlocklistRequest(@NotBlank String kind, @NotBlank @Size(max = 255) String value, @Size(max = 200) String reason) {}
    public record BlocklistEntry(Long id, String kind, String value, String reason) {}
    public record FeedbackRequest(@NotBlank @Size(max = 2048) String url,
                                  @NotBlank @Pattern(regexp = "FALSE_POSITIVE|FALSE_NEGATIVE") String kind,
                                  @Size(max = 300) String note) {}
    public record FeedbackEntry(Long id, String host, String kind, String note, String status, String resolution,
                                java.time.Instant createdAt) {}
    public record CloseFeedbackRequest(@NotBlank @Pattern(regexp = "ADD_TO_BLOCKLIST|ALLOW|DISMISS") String resolution) {}

    /** 5.3: counts for the dashboard. byType/bySeverity contain every key, zero when there are no rows. */
    public record ThreatSummary(long total, long open, java.util.Map<String, Long> byType, java.util.Map<String, Long> bySeverity) {}

    /** Mobile reports on-device findings (call forwarding, OTP, etc.) so they appear in history. */
    public record ReportRequest(@NotBlank String type, @NotBlank String severity,
                                @NotBlank String title, String detail, String sourceApp) {}
}
