package com.fraudshield.logging;

import java.util.regex.Pattern;

/**
 * 8.4: removes personal data from every log line and stack trace before it is written (wired in logback-spring.xml).
 * Our own log statements never print these, but framework messages can (e.g. a database error that quotes the
 * duplicate email, an exception message holding a scanned URL with its query string).
 */
public final class LogScrubber {
    private LogScrubber() {}

    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    /** keeps scheme + host + path (useful for debugging), drops the query and fragment (tokens, phone numbers, ids) */
    private static final Pattern URL_QUERY = Pattern.compile("((?:https?|ftp)://[^\\s?#\"'<>]+)[?#][^\\s\"'<>]*");
    /** Indian mobile numbers (+91 / 0 / none, 10 digits starting 6-9), also with spaces or dashes */
    private static final Pattern PHONE = Pattern.compile("(?<![\\d])(?:\\+?91[\\s-]?|0)?[6-9]\\d{4}[\\s-]?\\d{5}(?![\\d])");

    public static String scrub(String s) {
        if (s == null || s.isEmpty()) return s;
        String r = JWT.matcher(s).replaceAll("<jwt>");
        r = BEARER.matcher(r).replaceAll("$1<token>");
        r = URL_QUERY.matcher(r).replaceAll("$1?<query>");
        r = EMAIL.matcher(r).replaceAll("<email>");
        r = PHONE.matcher(r).replaceAll("<phone>");
        return r;
    }
}
