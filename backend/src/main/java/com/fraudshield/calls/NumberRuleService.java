package com.fraudshield.calls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 16.3: free number-pattern rules for the phone (GET /config/number-rules): TRAI 1600 / 140 series, odd lengths,
 * risky foreign country codes (configurable). {@link #classify} is the reference implementation the phone mirrors.
 */
@Service
public class NumberRuleService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ObjectNode rules;
    private final List<String> risky;

    public NumberRuleService(@Value("${fraudshield.calls.risky-country-codes:+92}") String riskyCodes) {
        this.risky = Arrays.stream(riskyCodes.split(",")).map(String::strip).filter(s -> s.matches("\\+?\\d{1,4}"))
                .map(s -> s.startsWith("+") ? s : "+" + s).distinct().sorted().toList();
        try (InputStream in = NumberRuleService.class.getResourceAsStream("/seed/number-rules.json")) {
            if (in == null) throw new IllegalStateException("seed/number-rules.json missing");
            ObjectNode n = (ObjectNode) JSON.readTree(in);
            ArrayNode codes = n.putArray("riskyCountryCodes");
            risky.forEach(codes::add);
            this.rules = n;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("seed/number-rules.json unreadable", e);
        }
    }

    public JsonNode rules() { return rules; }

    /** TRAI: 1600 (BFSI / government service) and 140 (promotional) calls may not be tagged or blocked. */
    public static boolean isProtected(String canonical) {
        String n = PhoneNumbers.indianNational(canonical);
        return n != null && (n.startsWith("1600") || n.startsWith("140"));
    }

    /** The first matching rule's id, or null. Same semantics as described in the JSON's "matching" text. */
    public String classify(String raw) {
        if (raw == null) return null;
        String s = raw.strip().replaceAll("[\\s\\-.()]", "");
        if (s.startsWith("00")) s = "+" + s.substring(2);
        String canonical = PhoneNumbers.canonical(s);
        String national = PhoneNumbers.indianNational(canonical);
        for (JsonNode r : rules.get("rules")) {
            boolean hit = switch (r.get("type").asText()) {
                case "NATIONAL_PREFIX" -> national != null && national.length() == r.get("length").asInt()
                        && national.startsWith(r.get("prefix").asText());
                case "COUNTRY_LENGTH" -> s.startsWith("+" + r.get("countryCode").asText())
                        && s.length() - 1 - r.get("countryCode").asText().length() != r.get("notLength").asInt();
                case "E164_LENGTH" -> s.startsWith("+") && (s.length() - 1 < r.get("minDigits").asInt() || s.length() - 1 > r.get("maxDigits").asInt());
                case "COUNTRY_CODE_IN" -> s.startsWith("+") && risky.stream().anyMatch(s::startsWith);
                case "NOT_COUNTRY" -> s.startsWith("+") && !s.startsWith("+" + r.get("countryCode").asText());
                default -> false;
            };
            if (hit) return r.get("id").asText();
        }
        return null;
    }
}
