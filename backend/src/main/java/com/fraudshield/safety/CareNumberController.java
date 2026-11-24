package com.fraudshield.safety;

import org.springframework.web.bind.annotation.*;

/** 15.9: check a customer-care number before calling it. The number is not stored or logged. */
@RestController
@RequestMapping("/api/v1/care-number")
public class CareNumberController {
    private final CareNumberService care;

    public CareNumberController(CareNumberService care) { this.care = care; }

    /** {@code GET /care-number/18001234?brand=SBI} - brand is optional (name or keyword from the brand list). */
    @GetMapping("/{number}")
    public CareNumberService.Result check(@PathVariable String number, @RequestParam(required = false) String brand) {
        return care.check(number, brand);
    }
}
