package com.fraudshield.calls;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Phase 16: the ONE definition of "the same number" shared by the server and the phone.
 * <ul>
 *   <li>canonical: E.164 text, e.g. "+919876543210". Spaces, '-', '.', '(', ')' are dropped; "00" becomes "+";
 *       a bare 10-digit number, "0" + 10 digits and "91" + 10 digits are Indian (+91). Anything else without a '+'
 *       (short codes like 1930, odd lengths) has no canonical form: null.</li>
 *   <li>hash: SHA-256 of the canonical text's UTF-8 bytes, lower-case hex (64 chars).</li>
 *   <li>prefix: the hash's first 5 hex characters (16.2 range lookups).</li>
 * </ul>
 */
public final class PhoneNumbers {
    private PhoneNumbers() {}

    /** E.164 ("+" and 8..15 digits; +91 must have exactly 10 national digits) or null. */
    public static String canonical(String raw) {
        if (raw == null) return null;
        String s = raw.strip().replaceAll("[\\s\\-.()]", "");
        if (s.startsWith("00")) s = "+" + s.substring(2);
        String d;
        if (s.startsWith("+")) {
            d = s.substring(1);
            if (!d.matches("\\d{8,15}")) return null;
        } else {
            if (!s.matches("\\d+")) return null;
            if (s.length() == 10) d = "91" + s;
            else if (s.length() == 11 && s.startsWith("0")) d = "91" + s.substring(1);
            else if (s.length() == 12 && s.startsWith("91")) d = s;
            else return null;
        }
        if (d.startsWith("91") && d.length() != 12) return null;
        if (d.startsWith("0")) return null;
        return "+" + d;
    }

    public static String hash(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 64 lower-case hex characters. */
    public static boolean isHash(String h) { return h != null && h.matches("[0-9a-f]{64}"); }

    /** The 10 national digits of an Indian number, else null. */
    public static String indianNational(String canonical) {
        return canonical != null && canonical.startsWith("+91") && canonical.length() == 13 ? canonical.substring(3) : null;
    }
}
