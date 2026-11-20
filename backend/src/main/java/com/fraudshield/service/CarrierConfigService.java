package com.fraudshield.service;

import com.fraudshield.domain.CarrierPattern;
import com.fraudshield.dto.Dtos.CarrierConfigResponse;
import com.fraudshield.dto.Dtos.CarrierRule;
import com.fraudshield.repo.Repositories.CarrierPatternRepository;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Service
public class CarrierConfigService {
    private final CarrierPatternRepository repo;

    public CarrierConfigService(CarrierPatternRepository repo) { this.repo = repo; }

    /** Enabled rules in evaluation order, plus a content hash the app uses as ETag / cache key. */
    public CarrierConfigResponse config() {
        List<CarrierPattern> rows = repo.findByEnabledTrueOrderByPriorityAscIdAsc();
        List<CarrierRule> rules = rows.stream()
                .map(p -> new CarrierRule(p.getCountry(), p.getCarrier(), p.getKind(), p.getRegex(), p.getPriority()))
                .toList();
        return new CarrierConfigResponse(version(rules), rules);
    }

    /** Server-side reference classifier, built from the same rules the app downloads. */
    public CarrierClassifier classifier() {
        return new CarrierClassifier(config().rules().stream()
                .map(r -> new CarrierClassifier.Rule(r.kind(), r.regex(), r.priority())).toList());
    }

    private static String version(List<CarrierRule> rules) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (CarrierRule r : rules) {
                md.update((r.country() + "|" + r.carrier() + "|" + r.kind() + "|" + r.regex() + "|" + r.priority() + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
