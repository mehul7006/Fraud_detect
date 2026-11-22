package com.fraudshield.controller;

import com.fraudshield.domain.Enums.Severity;
import com.fraudshield.domain.Enums.ThreatType;
import com.fraudshield.dto.Dtos.ReportRequest;
import com.fraudshield.dto.Dtos.ThreatDto;
import com.fraudshield.service.TakeoverService;
import com.fraudshield.service.ThreatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ThreatController {
    private final ThreatService threats;
    private final TakeoverService takeover;

    public ThreatController(ThreatService threats, TakeoverService takeover) { this.threats = threats; this.takeover = takeover; }

    @GetMapping("/threats")
    public List<ThreatDto> list(@AuthenticationPrincipal Long uid, @RequestParam(required = false) String status) {
        return threats.list(uid, status);
    }

    @GetMapping("/threats/summary")
    public com.fraudshield.dto.Dtos.ThreatSummary summary(@AuthenticationPrincipal Long uid) { return threats.summary(uid); }

    /**
     * On-device findings (call forwarding, unexpected OTP, WhatsApp/Telegram takeover) reported by the app.
     * 201 = new threat created. 200 = an identical OPEN threat already existed (returned, nothing added).
     */
    @PostMapping("/threats")
    public ResponseEntity<ThreatDto> report(@AuthenticationPrincipal Long uid, @Valid @RequestBody ReportRequest r) {
        ThreatType type;
        Severity sev;
        try {
            type = ThreatType.valueOf(r.type());
            sev = Severity.valueOf(r.severity());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown type or severity");
        }
        ThreatService.Reported res = threats.report(uid, type, sev, r.title(), r.detail(), r.sourceApp());
        takeover.check(uid); // 7.2: login code / linked-device notice + new device or SIM change = takeover
        return ResponseEntity.status(res.created() ? HttpStatus.CREATED : HttpStatus.OK).body(res.threat());
    }

    @PostMapping("/threats/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@AuthenticationPrincipal Long uid, @PathVariable Long id) { threats.resolve(uid, id); }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal Long uid) { threats.deleteAccount(uid); }
}
