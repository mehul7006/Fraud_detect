package com.fraudshield.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 6.2: decides whether an installed app really is the known-good app with that package name.
 *
 *  - known certificate + app's certificate matches  -> GENUINE (never flagged)
 *  - known certificate + app's certificate differs  -> FAKE    (same name, other developer: always flagged)
 *  - certificate not known yet + installed by Google Play -> GENUINE (Play never lets another developer
 *    publish under an existing package name)
 *  - anything else (sideloaded, old client without the data) -> UNVERIFIED (scored normally, like any app)
 */
public final class AppTrust {
    public enum Result { GENUINE, FAKE, UNVERIFIED }

    public static final Set<String> TRUSTED_INSTALLERS = Set.of("com.android.vending");
    private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");

    private AppTrust() {}

    /** "AB:CD:..." or "abcd..." in any case, several separated by , or ; -> set of 64-char lowercase hex. */
    public static Set<String> parseCerts(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split("[,;\\s]+"))
                .map(c -> c.replace(":", "").trim().toLowerCase(Locale.ROOT))
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** true when every entry is a valid SHA-256 hex digest (used to reject bad admin / CSV input). */
    public static boolean validCerts(String raw) {
        Set<String> c = parseCerts(raw);
        return !c.isEmpty() && c.stream().allMatch(x -> HEX64.matcher(x).matches());
    }

    public static String normalise(String raw) { return raw == null || raw.isBlank() ? null : String.join(",", parseCerts(raw)); }

    public static Result check(String knownCerts, String appCerts, String installer) {
        Set<String> known = parseCerts(knownCerts);
        Set<String> app = parseCerts(appCerts);
        if (!known.isEmpty() && !app.isEmpty()) {
            return app.stream().anyMatch(known::contains) ? Result.GENUINE : Result.FAKE;
        }
        if (known.isEmpty() && installer != null && TRUSTED_INSTALLERS.contains(installer)) return Result.GENUINE;
        return Result.UNVERIFIED;
    }
}
