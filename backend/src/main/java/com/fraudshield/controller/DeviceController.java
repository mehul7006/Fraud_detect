package com.fraudshield.controller;

import com.fraudshield.domain.Enums.Severity;
import com.fraudshield.domain.Enums.ThreatType;
import com.fraudshield.dto.Dtos.DeviceRequest;
import com.fraudshield.dto.Dtos.DeviceResponse;
import com.fraudshield.service.CloneRiskService;
import com.fraudshield.service.TakeoverService;
import com.fraudshield.service.ThreatService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    private final CloneRiskService clone;
    private final ThreatService threats;
    private final TakeoverService takeover;

    public DeviceController(CloneRiskService clone, ThreatService threats, TakeoverService takeover) {
        this.clone = clone; this.threats = threats; this.takeover = takeover;
    }

    @PostMapping
    public DeviceResponse register(@AuthenticationPrincipal Long uid, @Valid @RequestBody DeviceRequest r) {
        return handle(uid, r);
    }

    @PostMapping("/heartbeat")
    public DeviceResponse heartbeat(@AuthenticationPrincipal Long uid, @Valid @RequestBody DeviceRequest r) {
        return handle(uid, r);
    }

    private DeviceResponse handle(Long uid, DeviceRequest r) {
        DeviceResponse res = clone.register(uid, r);
        if (res.cloneRiskScore() >= 50) {
            threats.recordOnce(uid, ThreatType.CLONE_RISK, res.cloneRiskScore() >= 75 ? Severity.CRITICAL : Severity.HIGH,
                    "Possible phone clone / remote access", String.join("; ", res.reasons()), "device");
        }
        takeover.check(uid); // 7.2: new device / SIM change + recent login code = takeover
        return res;
    }
}
