package com.fraudshield.domain;

public final class Enums {
    private Enums() {}

    public enum Verdict { SAFE, SUSPICIOUS, MALICIOUS;
        public static Verdict fromScore(int s) { return s >= 70 ? MALICIOUS : s >= 35 ? SUSPICIOUS : SAFE; }
    }
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum ThreatType {
        MALICIOUS_LINK, MALICIOUS_FILE, AD_ABUSE, CALL_FORWARDING, SMS_FORWARDING,
        CLONE_RISK, WHATSAPP_TAKEOVER, TELEGRAM_TAKEOVER, UNEXPECTED_OTP,
        /** 7.2: correlated - a login code / linked-device notice AND a new device or SIM change within minutes. */
        ACCOUNT_TAKEOVER,
        /** 15.1: correlated - long call from an unknown number + remote-access app + banking/UPI app + OTP, within minutes. */
        LIVE_SCAM
    }
}
