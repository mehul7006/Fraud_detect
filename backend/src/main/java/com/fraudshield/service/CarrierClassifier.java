package com.fraudshield.service;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Classifies a carrier's answer to the call-forwarding USSD query (*#21# etc.) as ACTIVE / INACTIVE / UNKNOWN.
 * Pure logic, no Spring: the same rules and the same sample texts are reused by the Flutter side (FE 3.4).
 * Rules run in ascending priority; the first match wins.
 */
public final class CarrierClassifier {
    public enum State { ACTIVE, INACTIVE, UNKNOWN }

    public record Rule(String kind, String regex, int priority) {}

    private static final int MAX_TEXT = 500;      // carriers answer in a few lines; cap protects against regex abuse
    private static final int MAX_REGEX = 200;

    private record Compiled(State state, Pattern pattern, int priority) {}

    private final List<Compiled> rules;

    public CarrierClassifier(List<Rule> source) {
        this.rules = source.stream()
                .filter(r -> r.regex() != null && r.regex().length() <= MAX_REGEX)
                .map(CarrierClassifier::compile)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(Compiled::priority))
                .toList();
    }

    private static Compiled compile(Rule r) {
        try {
            State s = "ACTIVE".equalsIgnoreCase(r.kind()) ? State.ACTIVE
                    : "INACTIVE".equalsIgnoreCase(r.kind()) ? State.INACTIVE : null;
            return s == null ? null : new Compiled(s, Pattern.compile(r.regex()), r.priority());
        } catch (PatternSyntaxException bad) {
            return null;                          // a broken server rule must never break the app
        }
    }

    public State classify(String response) {
        if (response == null || response.isBlank()) return State.UNKNOWN;
        String text = response.length() > MAX_TEXT ? response.substring(0, MAX_TEXT) : response;
        for (Compiled c : rules) {
            if (c.pattern().matcher(text).find()) return c.state();
        }
        return State.UNKNOWN;
    }

    public int ruleCount() { return rules.size(); }
}
