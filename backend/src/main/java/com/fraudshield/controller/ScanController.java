package com.fraudshield.controller;

import com.fraudshield.domain.Enums.Severity;
import com.fraudshield.domain.Enums.ThreatType;
import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.*;
import com.fraudshield.service.AdAbuseService;
import com.fraudshield.service.FileScanService;
import com.fraudshield.service.ThreatService;
import com.fraudshield.service.UrlScanService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scan")
public class ScanController {
    private final UrlScanService urls;
    private final FileScanService files;
    private final AdAbuseService ads;
    private final ThreatService threats;

    public ScanController(UrlScanService urls, FileScanService files, AdAbuseService ads, ThreatService threats) {
        this.urls = urls; this.files = files; this.ads = ads; this.threats = threats;
    }

    @PostMapping("/url")
    public ScanResponse url(@AuthenticationPrincipal Long uid, @Valid @RequestBody UrlScanRequest r) {
        ScanResponse res = urls.scan(r.url());
        if (res.verdict() != Verdict.SAFE) {
            threats.record(uid, ThreatType.MALICIOUS_LINK,
                    res.verdict() == Verdict.MALICIOUS ? Severity.CRITICAL : Severity.MEDIUM,
                    "Dangerous link blocked", host(r.url()) + " - " + String.join(", ", res.reasons()), r.sourceApp());
        }
        return res;
    }

    @PostMapping("/file")
    public FileScanResponse file(@AuthenticationPrincipal Long uid, @Valid @RequestBody FileScanRequest r) {
        FileScanResponse res = files.scan(r);
        if (res.verdict() != Verdict.SAFE) {
            threats.record(uid, ThreatType.MALICIOUS_FILE,
                    res.verdict() == Verdict.MALICIOUS ? Severity.CRITICAL : Severity.MEDIUM,
                    "Suspicious file: " + r.fileName(), String.join(", ", res.reasons()), r.sourceApp());
        }
        return res;
    }

    @PostMapping("/apps")
    public AppsScanResponse apps(@AuthenticationPrincipal Long uid, @Valid @RequestBody AppsScanRequest r) {
        var results = ads.scanAll(r.apps());
        results.stream().filter(x -> x.verdict() == Verdict.MALICIOUS).forEach(x ->
                threats.record(uid, ThreatType.AD_ABUSE, Severity.HIGH,
                        "App spamming ads / suspicious: " + x.packageName(), String.join(", ", x.reasons()), x.packageName()));
        return new AppsScanResponse(results);
    }

    private static String host(String u) {
        try { return java.net.URI.create(u.contains("://") ? u : "http://" + u).getHost(); }
        catch (Exception e) { return "unknown"; }
    }
}
