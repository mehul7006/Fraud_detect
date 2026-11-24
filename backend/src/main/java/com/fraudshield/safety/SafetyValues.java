package com.fraudshield.safety;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Phase 15: one normalisation + hash rule for every reported value, shared with the phone
 * (the app can send either the value or {@code sha256(normalise(type, value))} as lowercase hex).
 * <ul>
 *   <li>UPI: trimmed, lower-case ({@code Name.Scam@YBL} -> {@code name.scam@ybl})</li>
 *   <li>LINK: the host only, lower-case, without scheme, port, path, a leading {@code www.} or a trailing dot</li>
 * </ul>
 * Phone numbers are NOT reported here: they go to the Phase 16 store ({@code PhoneNumbers}, E.164 hash).
 * {@link #number} is only the matching key of the 15.9 customer-care directory: digits only; {@code +91 / 0091 / 91}
 * and a single trunk {@code 0} before 10 digits dropped ({@code +91 98765 43210} -> {@code 9876543210}); toll-free
 * {@code 1800...} and short codes ({@code 1930}) stay as they are.
 */
public final class SafetyValues {
    private SafetyValues() {}

    static final Pattern HASH = Pattern.compile("^[0-9a-f]{64}$");

    public static String normalise(CrowdItem.Type type, String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        return switch (type) {
            case UPI -> v.toLowerCase(Locale.ROOT);
            case LINK -> host(v);
        };
    }

    public static String hash(CrowdItem.Type type, String raw) { return sha256(normalise(type, raw)); }

    public static boolean isHash(String s) { return s != null && HASH.matcher(s).matches(); }

    static String host(String v) {
        String h = v.toLowerCase(Locale.ROOT).replaceFirst("^[a-z][a-z0-9+.-]*://", "");
        h = h.split("[/?#]", 2)[0];
        int at = h.lastIndexOf('@');
        if (at >= 0) h = h.substring(at + 1); // user-info trick: https://sbi.co.in@evil.top
        h = h.replaceFirst(":\\d+$", "");
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.startsWith("www.")) h = h.substring(4);
        return h;
    }

    public static String number(String v) {
        String d = v.replaceAll("\\D", "");
        if (d.length() == 14 && d.startsWith("0091")) d = d.substring(4);
        else if (d.length() == 12 && d.startsWith("91")) d = d.substring(2);
        else if (d.length() == 13 && d.startsWith("910")) d = d.substring(3); // +91 022 ... (country code AND trunk 0)
        else if (d.length() == 11 && d.startsWith("0")) d = d.substring(1);
        return d;
    }

    /** A 10-digit Indian mobile number (starts with 6-9). */
    public static boolean isMobile(String normalisedNumber) {
        return normalisedNumber.length() == 10 && normalisedNumber.charAt(0) >= '6' && normalisedNumber.charAt(0) <= '9';
    }

    /**
     * Like {@link #isMobile} for what the user typed. A number written with a leading trunk 0 ("080-2659 9990",
     * "079-...") is taken as a landline: STD codes such as 80 (Bengaluru) or 79 (Ahmedabad) also start with 6-9.
     */
    public static boolean looksMobile(String raw) {
        String d = raw == null ? "" : raw.replaceAll("\\D", "");
        if (d.startsWith("0") && !d.startsWith("0091")) return false;
        return isMobile(number(raw));
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
