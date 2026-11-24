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

    /** 12.6: the VPN sends only a host name - no path, no query, nothing else. */
    public record DomainScanRequest(@NotBlank @Size(max = 253)
                                    @jakarta.validation.constraints.Pattern(regexp = "^(?=.{1,253}$)([a-zA-Z0-9_]([a-zA-Z0-9_-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z0-9-]{2,63}\\.?$|^xn--[a-z0-9.-]+$")
                                    String domain) {}
    public record DomainScanResponse(Verdict verdict, int score, List<String> reasonCodes, String impersonatedBrand,
                                     String officialUrl) {}
    public record UrlScanRequest(@NotBlank @Size(max = 2048) String url, String sourceApp) {}
    /**
     * 10.9: {@code reasonCodes} are stable (LOOKALIKE_BRAND, NEW_DOMAIN, ...); {@code impersonatedBrand} and
     * {@code officialUrl} are set when the link imitates a known brand ("looks like Flipkart, real site: ...").
     */
    public record ScanResponse(Verdict verdict, int score, List<String> reasons, List<String> reasonCodes,
                               String impersonatedBrand, String officialUrl, Integer mlScore,
                               java.util.Map<String, String> explanation) {
        /** 11.4: {@code mlScore} 0-100 from the link model; null when it was not used (known site, model off).
         *  11.6: {@code explanation} = {en, hi, source: template|ai}, at most two sentences; null for SAFE. */
        public ScanResponse(Verdict verdict, int score, List<String> reasons) { this(verdict, score, reasons, List.of(), null, null, null, null); }
        public ScanResponse withExplanation(java.util.Map<String, String> e) {
            return new ScanResponse(verdict, score, reasons, reasonCodes, impersonatedBrand, officialUrl, mlScore, e);
        }
    }

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
                          @Size(max = 400) String certSha256, @Size(max = 200) String installer,
                          @Size(max = 300) List<@Size(max = 150) String> requestedPermissions,
                          @Size(max = 300) List<@Size(max = 150) String> grantedPermissions,
                          Integer targetSdk,
                          @Size(max = 100) List<@Size(max = 100) String> trackers,
                          @Size(max = 300) List<@Size(max = 253) String> domainsContacted,
                          @Size(max = 40) String appCategory) {
        /** 13.x privacy data (all optional - older clients send none): permissions the app asks for / was given,
         *  its target SDK, tracker SDK names found inside it (13.4), domains it looked up (12.8 / 13.5),
         *  Android's app category (ApplicationInfo.category name, e.g. "GAME", "SOCIAL"). */
        public AppInfo(String packageName, String label, int notifPerHour, boolean hasOverlayPermission,
                       boolean hasSmsPermission, boolean hasAccessibility, boolean hasNotificationListener,
                       boolean hasFullScreenIntent, boolean isSystem, int selfLaunchCount, String certSha256, String installer) {
            this(packageName, label, notifPerHour, hasOverlayPermission, hasSmsPermission, hasAccessibility, hasNotificationListener,
                 hasFullScreenIntent, isSystem, selfLaunchCount, certSha256, installer, null, null, null, null, null, null);
        }
        public boolean hasPrivacyData() {
            return requestedPermissions != null || grantedPermissions != null || trackers != null || domainsContacted != null;
        }
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
    /**
     * 13.7: {@code verdict} = the worse of the ad-abuse and the privacy verdict. Privacy fields are null when the phone
     * sent no privacy data (older clients). {@code reasonCodes} are stable codes of the privacy reasons;
     * {@code impersonatedBrand} set for a fake copy of an official app (13.12/13.13); explanation = {en, hi, source}.
     */
    public record AppResult(String packageName, int adAbuseScore, Verdict verdict, List<String> reasons,
                            Boolean newSinceLastScan, Integer privacyScore, List<String> privacyReasons,
                            List<String> reasonCodes, String impersonatedBrand, java.util.Map<String, String> explanation) {
        public AppResult(String packageName, int adAbuseScore, Verdict verdict, List<String> reasons) {
            this(packageName, adAbuseScore, verdict, reasons, null, null, null, null, null, null);
        }
        public AppResult(String packageName, int adAbuseScore, Verdict verdict, List<String> reasons, Boolean newSinceLastScan) {
            this(packageName, adAbuseScore, verdict, reasons, newSinceLastScan, null, null, null, null, null);
        }
        public AppResult withNewSinceLastScan(Boolean b) {
            return new AppResult(packageName, adAbuseScore, verdict, reasons, b, privacyScore, privacyReasons, reasonCodes, impersonatedBrand, explanation);
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

    /** 11.10: explanationEn / explanationHi = "why is this risky?" (null for older threats and non-link threats). */
    public record ThreatDto(Long id, String type, String severity, String title, String detail,
                            String sourceApp, String status, String createdAt, String explanationEn, String explanationHi) {}

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
