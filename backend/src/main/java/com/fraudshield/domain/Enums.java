package com.fraudshield.domain;

public final class Enums {
    private Enums() {}

    public enum Verdict { SAFE, SUSPICIOUS, MALICIOUS;
        public static Verdict fromScore(int s) { return s >= 70 ? MALICIOUS : s >= 35 ? SUSPICIOUS : SAFE; }
    }
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum ThreatType {
        MALICIOUS_LINK, MALICIOUS_FILE, AD_ABUSE, CALL_FORWARDING, SMS_FORWARDING,
        CLONE_RISK, WHATSAPP_TAKEOVER, TELEGRAM_TAKEOVER, UNEXPECTED_OTP
    }
}
