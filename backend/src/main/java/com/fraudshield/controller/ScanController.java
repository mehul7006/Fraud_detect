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
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scan")
public class ScanController {
    private final UrlScanService urls;
    private final FileScanService files;
    private final AdAbuseService ads;
    private final ThreatService threats;
    private final com.fraudshield.service.DeviceAppService deviceApps;
    private final com.fraudshield.repo.Repositories.AppLabelRepository labels;
    private final com.fraudshield.service.AppScanService appScan; // 13.7 ad-abuse + privacy / fraud

    public ScanController(UrlScanService urls, FileScanService files, AdAbuseService ads, ThreatService threats,
                          com.fraudshield.service.DeviceAppService deviceApps,
                          com.fraudshield.repo.Repositories.AppLabelRepository labels,
                          com.fraudshield.service.AppScanService appScan) {
        this.urls = urls; this.files = files; this.ads = ads; this.threats = threats; this.deviceApps = deviceApps;
        this.labels = labels; this.appScan = appScan;
    }

    /** 6.1: opt-in training label. One row per user and app; a later decision replaces the earlier one. */
    @PostMapping("/apps/label")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @org.springframework.transaction.annotation.Transactional
    public void label(@AuthenticationPrincipal Long uid, @Valid @RequestBody AppLabelRequest r) {
        String pkg = r.app().packageName().trim();
        var l = labels.findByUserIdAndPackageName(uid, pkg).orElseGet(() -> new com.fraudshield.domain.AppLabel(uid, pkg));
        l.set(r.label(), r.app(), r.scoreShown());
        labels.save(l);
    }

    @PostMapping("/url")
    public ScanResponse url(@AuthenticationPrincipal Long uid, @Valid @RequestBody UrlScanRequest r) {
        ScanResponse res = urls.scan(r.url());
        if (res.verdict() != Verdict.SAFE) {
            threats.record(uid, ThreatType.MALICIOUS_LINK,
                    res.verdict() == Verdict.MALICIOUS ? Severity.CRITICAL : Severity.MEDIUM,
                    "Dangerous link blocked", host(r.url()) + " - " + String.join(", ", res.reasons()), r.sourceApp(),
                    res.explanation());
        }
        return res;
    }

    /**
     * 12.6: the VPN's question "is this domain safe?". Nothing is stored and nothing is logged: no threat record, so
     * no domain is ever linked to the user (the phone shows and keeps the alert itself).
     */
    @PostMapping("/domain")
    public DomainScanResponse domain(@Valid @RequestBody DomainScanRequest r) {
        String d = r.domain().toLowerCase(java.util.Locale.ROOT).replaceAll("\\.$", "");
        ScanResponse s = urls.scan("https://" + d + "/");
        // reasons that only make sense for a full link are left out (the phone never had one)
        var codes = s.reasonCodes().stream().filter(c -> !c.equals("NO_HTTPS") && !c.equals("BAIT_WORDS") && !c.equals("BRAND_IN_PATH")).toList();
        return new DomainScanResponse(s.verdict(), s.score(), codes, s.impersonatedBrand(), s.officialUrl());
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
        var results = appScan.scanAll(r.apps());
        List<AppResult> toRecord;
        if (r.deviceId() != null && !r.deviceId().isBlank()) {
            // 6.3: remember the device's apps; record a threat only when an app BECOMES malicious (not every hour)
            var outcome = deviceApps.track(uid, r.deviceId(), results);
            results = outcome.results();
            toRecord = outcome.newlyMalicious();
        } else {
            toRecord = results.stream().filter(x -> x.verdict() == Verdict.MALICIOUS).toList(); // old clients
        }
        toRecord.forEach(x -> {
            boolean fraud = x.reasonCodes() != null && !x.reasonCodes().isEmpty() && !x.reasonCodes().equals(List.of("AD_ABUSE"));
            var why = new java.util.ArrayList<>(x.reasons());
            if (x.privacyReasons() != null) why.addAll(x.privacyReasons());
            threats.record(uid, ThreatType.AD_ABUSE, Severity.HIGH,
                    (x.impersonatedBrand() != null ? "Fake " + x.impersonatedBrand() + " app: "
                            : fraud ? "Risky app: " : "App spamming ads / suspicious: ") + x.packageName(),
                    String.join(", ", why), x.packageName(), x.explanation());
        });
        return new AppsScanResponse(results);
    }

    private static String host(String u) {
        try { return java.net.URI.create(u.contains("://") ? u : "http://" + u).getHost(); }
        catch (Exception e) { return "unknown"; }
    }
}
