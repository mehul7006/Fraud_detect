package com.fraudshield.safety;

import com.fraudshield.calls.NumberReputationService;
import com.fraudshield.safety.CrowdItem.Status;
import com.fraudshield.safety.CrowdItem.Type;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 15.7: crowd reports (any signed-in user) and the moderation queue (ADMIN, under /admin/**).
 * Websites and UPI IDs are kept here; a phone number ({@code NUMBER} / {@code CARE_NUMBER}) is handed to the Phase 16
 * number store (one store, one vote per user per number, same trust idea) - send its plain value, not a hash.
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {
    public record ReportRequest(@NotNull @Size(max = 16) String type, @Size(max = 500) String value, @Size(max = 64) String valueHash) {}
    public record ReportResponse(boolean counted, Status status) {}
    public record StatusResponse(Type type, Status status, int reportCount) {}
    public record ReviewRequest(@NotNull CrowdReportService.Decision decision) {}

    private final CrowdReportService crowd;
    private final NumberReputationService numbers;

    public ReportController(CrowdReportService crowd, NumberReputationService numbers) { this.crowd = crowd; this.numbers = numbers; }

    /** 202 always for a valid report: {@code counted=false} when this user had already reported the item. */
    @PostMapping("/reports")
    public ResponseEntity<ReportResponse> report(@AuthenticationPrincipal Long uid, @Valid @RequestBody ReportRequest r) {
        String t = r.type().strip().toUpperCase(java.util.Locale.ROOT);
        if (t.equals("NUMBER") || t.equals("CARE_NUMBER")) {
            if (r.value() == null || r.value().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "for a phone number send its value (or use POST /numbers/report)");
            var n = numbers.report(uid, r.value(), "SCAM");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ReportResponse(n.counted(),
                    "SUSPICIOUS".equals(n.status()) ? Status.SUSPICIOUS : Status.UNVERIFIED));
        }
        Type type;
        try { type = Type.valueOf(t); } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be LINK, UPI or NUMBER");
        }
        var o = crowd.report(uid, type, r.value(), r.valueHash());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ReportResponse(o.counted(), o.status()));
    }

    /** What the community says about one item (by value or by hash). */
    @GetMapping("/reports/status")
    public StatusResponse status(@RequestParam Type type, @RequestParam(required = false) String value,
                                 @RequestParam(required = false) String valueHash) {
        String hash = value != null && !value.isBlank() ? SafetyValues.hash(type, value) : valueHash;
        if (!SafetyValues.isHash(hash)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "give value or valueHash");
        return crowd.find(type, hash).map(i -> new StatusResponse(type, i.getStatus(), i.getReportCount()))
                .orElse(new StatusResponse(type, Status.UNVERIFIED, 0));
    }

    @GetMapping("/admin/reports/queue")
    public List<CrowdReportService.ItemView> queue() { return crowd.queue(); }

    @PostMapping("/admin/reports/{id}/review")
    public CrowdReportService.ItemView review(@PathVariable Long id, @Valid @RequestBody ReviewRequest r) {
        return crowd.review(id, r.decision());
    }
}
