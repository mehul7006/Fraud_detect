package com.fraudshield.safety;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 15.5: "check before you pay" for a UPI ID or a scanned QR code. Nothing about the user is stored or logged. */
@RestController
@RequestMapping("/api/v1/scan/upi")
public class UpiScanController {
    public record Request(@Size(max = 300) String vpa, @Size(max = 2000) String qrPayload) {}

    private final UpiScanService upi;

    public UpiScanController(UpiScanService upi) { this.upi = upi; }

    @PostMapping
    public UpiScanService.Result scan(@Valid @RequestBody Request r) {
        if (r.qrPayload() != null && !r.qrPayload().isBlank()) return upi.scanQr(r.qrPayload());
        if (r.vpa() != null && !r.vpa().isBlank()) return upi.scanVpa(r.vpa());
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "give vpa or qrPayload");
    }
}
