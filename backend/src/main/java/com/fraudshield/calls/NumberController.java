package com.fraudshield.calls;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Phase 16 call guard endpoints. All need a logged-in user (admin ones ADMIN); all are rate-limited (8.1). */
@RestController
@RequestMapping("/api/v1")
public class NumberController {
    public record ReportRequest(@NotBlank @Size(max = 32) String number, @NotBlank @Size(max = 20) String category) {
        @Override public String toString() { return "ReportRequest[number=<hidden>, category=" + category + "]"; }
    }

    private final NumberReputationService reputation;
    private final NumberRuleService rules;

    public NumberController(NumberReputationService reputation, NumberRuleService rules) {
        this.reputation = reputation; this.rules = rules;
    }

    /** 16.2 k-anonymity lookup: the phone sends only the first 5 hex chars of the number's hash. */
    @GetMapping("/numbers/range/{prefix}")
    public NumberReputationService.Range range(@PathVariable String prefix) { return reputation.range(prefix); }

    /** 16.5 crowd report. */
    @PostMapping("/numbers/report")
    public NumberReputationService.Report report(@AuthenticationPrincipal Long uid, @Valid @RequestBody ReportRequest r) {
        return reputation.report(uid, r.number(), r.category());
    }

    /** 16.3 free pattern rules. */
    @GetMapping("/config/number-rules")
    public JsonNode numberRules() { return rules.rules(); }

    /** 16.4 signed delta of SUSPICIOUS number hashes. */
    @GetMapping("/config/numbers")
    public NumberReputationService.Delta numbers(@RequestParam(defaultValue = "0") long since) { return reputation.since(since); }

    /** Admin: un-flag a wrongly reported number (by hash). */
    @PostMapping("/admin/numbers/{hash}/clear")
    public Map<String, Boolean> clear(@PathVariable String hash) {
        if (!reputation.clear(hash)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        return Map.of("cleared", true);
    }
}
