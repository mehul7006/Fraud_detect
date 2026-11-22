package com.fraudshield.controller;

import com.fraudshield.domain.Blocklist;
import com.fraudshield.domain.UrlFeedback;
import com.fraudshield.dto.Dtos.*;
import com.fraudshield.repo.Repositories.UrlFeedbackRepository;
import com.fraudshield.service.AppReputationService;
import com.fraudshield.service.BlocklistService;
import com.fraudshield.service.BlocklistService.ImportResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** ADMIN-only endpoints (enforced in SecurityConfig for /api/v1/admin/**). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final BlocklistService blocklist;
    private final UrlFeedbackRepository feedback;
    private final com.fraudshield.repo.Repositories.AppReputationRepository apps;
    private final AppReputationService appService;
    private final com.fraudshield.repo.Repositories.AppLabelRepository labels;

    public AdminController(BlocklistService blocklist, UrlFeedbackRepository feedback,
                           com.fraudshield.repo.Repositories.AppReputationRepository apps, AppReputationService appService,
                           com.fraudshield.repo.Repositories.AppLabelRepository labels) {
        this.blocklist = blocklist; this.feedback = feedback; this.apps = apps; this.appService = appService;
        this.labels = labels;
    }

    /**
     * 6.1: real labelled data in the format of src/test/resources/adabuse-labelled.csv. Save it as
     * src/test/resources/adabuse-real.csv and AdAbuseAccuracyTest measures the weights on it.
     */
    @GetMapping(value = "/apps/labels.csv", produces = "text/csv")
    public String labelsCsv() {
        StringBuilder b = new StringBuilder("# REAL opt-in beta labels (Uninstall = ABUSIVE, Ignore = BENIGN), exported ")
            .append(java.time.LocalDate.now())
            .append("\nlabel,name,notifPerHour,overlay,sms,accessibility,listener,fullScreen,isSystem,selfLaunch\n");
        for (var l : labels.findAllByOrderByIdAsc()) {
            b.append(l.getLabel()).append(',').append(l.getPackageName()).append(',').append(l.getNotifPerHour()).append(',')
             .append(l.isOverlay()).append(',').append(l.isSms()).append(',').append(l.isAccessibility()).append(',')
             .append(l.isListener()).append(',').append(l.isFullScreen()).append(",false,").append(l.getSelfLaunch()).append('\n');
        }
        return b.toString();
    }

    /** 6.2: list known GOOD or BAD apps. */
    @GetMapping("/apps")
    public List<AppReputationEntry> apps(@RequestParam(defaultValue = "BAD") String verdict) {
        return apps.findByVerdictOrderByPackageName(verdict.toUpperCase()).stream()
                .map(AdminController::dto).toList();
    }

    /** 6.2: add or change an app's reputation (BAD = always flagged, GOOD = never flagged). */
    @PostMapping("/apps")
    public AppReputationEntry setApp(@Valid @RequestBody AppReputationRequest r) {
        return dto(appService.set(r.packageName(), r.label(), r.verdict(), r.certSha256(), "admin", true));
    }

    /** 6.2: bulk import (e.g. the top-500 list). CSV package_name,label,verdict,cert_sha256; existing rows are updated. */
    @PostMapping(value = "/apps/import", consumes = "multipart/form-data")
    public AppReputationService.ImportResult importApps(@RequestParam("file") MultipartFile file,
                                                        @RequestParam(defaultValue = "import") String source) throws IOException {
        String src = source.replaceAll("[^A-Za-z0-9_.-]", "");
        return appService.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8),
                src.isEmpty() ? "import" : src.substring(0, Math.min(60, src.length())), true);
    }

    @GetMapping("/blocklist")
    public List<BlocklistEntry> list() { return blocklist.list().stream().map(AdminController::dto).toList(); }

    @PostMapping("/blocklist")
    @ResponseStatus(HttpStatus.CREATED)
    public BlocklistEntry add(@Valid @RequestBody BlocklistRequest r) {
        return dto(blocklist.add(r.kind(), r.value(), r.reason()));
    }

    @DeleteMapping("/blocklist/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { blocklist.delete(id); }

    @PostMapping(value = "/blocklist/import", consumes = "multipart/form-data")
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        return blocklist.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
    }

    @GetMapping("/feedback")
    public List<FeedbackEntry> queue(@RequestParam(defaultValue = "OPEN") String status) {
        return feedback.findByStatusOrderByCreatedAtAsc(status.toUpperCase()).stream().map(AdminController::dto).toList();
    }

    @PostMapping("/feedback/{id}/close")
    @Transactional
    public FeedbackEntry close(@PathVariable Long id, @Valid @RequestBody CloseFeedbackRequest r) {
        UrlFeedback f = feedback.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        if ("ADD_TO_BLOCKLIST".equals(r.resolution())) blocklist.addDomainIfMissing(f.getHost(), "Confirmed by user report");
        f.close(r.resolution());
        return dto(f);
    }

    private static AppReputationEntry dto(com.fraudshield.domain.AppReputation a) {
        return new AppReputationEntry(a.getPackageName(), a.getLabel(), a.getVerdict(), a.getSource(), a.getCertSha256());
    }
    private static BlocklistEntry dto(Blocklist b) { return new BlocklistEntry(b.getId(), b.getKind(), b.getValue(), b.getReason()); }
    private static FeedbackEntry dto(UrlFeedback f) {
        return new FeedbackEntry(f.getId(), f.getHost(), f.getKind(), f.getNote(), f.getStatus(), f.getResolution(), f.getCreatedAt());
    }
}
