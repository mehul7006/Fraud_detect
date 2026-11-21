package com.fraudshield.dto;

import com.fraudshield.domain.Enums.Verdict;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    public record AuthRequest(@Email @NotBlank String email, @Size(min = 8, max = 72) String password) {}
    public record TokenResponse(String token) {}

    public record DeviceRequest(@NotBlank String deviceId, String model, String osVersion, String simHash,
                                String fingerprint, boolean rooted, boolean emulator, boolean playProtectOn,
                                List<String> remoteAppsInstalled) {}
    public record DeviceResponse(String deviceId, int cloneRiskScore, List<String> reasons) {}

    public record UrlScanRequest(@NotBlank @Size(max = 2048) String url, String sourceApp) {}
    public record ScanResponse(Verdict verdict, int score, List<String> reasons) {}

    public record FileScanRequest(@NotBlank @Size(min = 64, max = 64) String sha256, @NotBlank String fileName,
                                  long sizeBytes, String mimeType, String sourceApp, String headerHex) {}
    public record FileScanResponse(Verdict verdict, int score, List<String> reasons, String action) {}

    public record AppInfo(@NotBlank String packageName, String label, int notifPerHour,
                          boolean hasOverlayPermission, boolean hasSmsPermission,
                          boolean hasAccessibility, boolean hasNotificationListener,
                          boolean hasFullScreenIntent, boolean isSystem) {}
    public record AppsScanRequest(@Valid @NotNull List<AppInfo> apps) {}
    public record AppResult(String packageName, int adAbuseScore, Verdict verdict, List<String> reasons) {}
    public record AppsScanResponse(List<AppResult> results) {}

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

    /** Mobile reports on-device findings (call forwarding, OTP, etc.) so they appear in history. */
    public record ReportRequest(@NotBlank String type, @NotBlank String severity,
                                @NotBlank String title, String detail, String sourceApp) {}
}
