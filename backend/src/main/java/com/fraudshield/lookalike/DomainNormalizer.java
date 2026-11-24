package com.fraudshield.lookalike;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 10.2: turns any link or host into its parts - host, registrable domain (Mozilla Public Suffix List, ICANN and
 * private sections, so {@code x.netlify.app} and {@code x.co.in} are cut correctly), punycode decoded, {@code www}
 * stripped - and spots sub-domain tricks such as {@code login.flipkart.com.evil.xyz} (registrable = {@code evil.xyz},
 * embedded domain = {@code flipkart.com}).
 * Thread-safe and immutable after construction; the list is read once (about 10k rules).
 */
public final class DomainNormalizer {
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern LATIN = Pattern.compile("\\p{IsLatin}");
    private static final Pattern NON_LATIN_LETTER = Pattern.compile("[\\p{L}&&[^\\p{IsLatin}]]");

    private final Set<String> rules = new HashSet<>();
    private final Set<String> wildcards = new HashSet<>();   // "*.ck" stored as "ck"
    private final Set<String> exceptions = new HashSet<>();  // "!www.ck" stored as "www.ck"

    public DomainNormalizer(InputStream psl) {
        try (var r = new BufferedReader(new InputStreamReader(psl, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("//")) continue;
                line = line.split("\\s")[0].toLowerCase(Locale.ROOT);
                if (line.startsWith("!")) exceptions.add(line.substring(1));
                else if (line.startsWith("*.")) wildcards.add(line.substring(2));
                else rules.add(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (rules.size() < 1000) throw new IllegalStateException("public suffix list looks truncated: " + rules.size());
    }

    /** The list shipped in {@code psl/public_suffix_list.dat}. */
    public static DomainNormalizer fromClasspath() {
        try (var in = DomainNormalizer.class.getResourceAsStream("/psl/public_suffix_list.dat")) {
            if (in == null) throw new IllegalStateException("psl/public_suffix_list.dat missing");
            return new DomainNormalizer(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Accepts a full URL ({@code https://u:p@Host.com:8080/x}) or a bare host. Returns null when there is no host. */
    public NormalizedDomain normalize(String urlOrHost) {
        if (urlOrHost == null) return null;
        String host = extractHost(urlOrHost.strip());
        if (host == null || host.isEmpty()) return null;
        host = host.toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.startsWith("[") || host.contains(":")) { // IPv6 literal
            String ip = host.replace("[", "").replace("]", "");
            return new NormalizedDomain(ip, ip, ip, ip, "", "", null, true, false, false);
        }
        if (IPV4.matcher(host).matches()) return new NormalizedDomain(host, host, host, host, "", "", null, true, false, false);

        String ascii, unicode;
        try {
            ascii = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
            unicode = IDN.toUnicode(ascii, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) { // label too long, bad punycode...
            ascii = host; unicode = host;
        }
        boolean idn = !ascii.equals(unicode) || ascii.contains("xn--");
        // "www." is noise - unless what is left is itself a public suffix ("www.gov.uk" is a site of its own)
        if (unicode.startsWith("www.") && unicode.indexOf('.', 4) > 0 && !publicSuffix(unicode.substring(4)).equals(unicode.substring(4))) {
            unicode = unicode.substring(4); ascii = ascii.substring(4);
        }

        String suffix = publicSuffix(unicode);
        String registrable;
        if (suffix.equals(unicode)) registrable = unicode; // the host IS a public suffix (e.g. "netlify.app")
        else {
            String rest = unicode.substring(0, unicode.length() - suffix.length() - 1);
            registrable = rest.substring(rest.lastIndexOf('.') + 1) + "." + suffix;
        }
        String sub = unicode.length() > registrable.length() ? unicode.substring(0, unicode.length() - registrable.length() - 1) : "";
        String label = registrable.equals(suffix) ? "" : registrable.substring(0, registrable.length() - suffix.length() - 1);
        return new NormalizedDomain(unicode, ascii, registrable, suffix, sub, label, embeddedDomain(sub),
                false, idn, mixedScript(label));
    }

    /** Longest matching public-suffix rule; unknown TLDs fall back to the last label (PSL default rule "*"). */
    public String publicSuffix(String host) {
        String[] p = host.split("\\.");
        for (int i = 0; i < p.length; i++) {
            String cand = String.join(".", java.util.Arrays.copyOfRange(p, i, p.length));
            if (exceptions.contains(cand)) return cand.substring(cand.indexOf('.') + 1);
            if (rules.contains(cand)) return cand;
            if (i + 1 < p.length && wildcards.contains(String.join(".", java.util.Arrays.copyOfRange(p, i + 1, p.length))))
                return cand;
        }
        return p[p.length - 1];
    }

    /** True when the host's last part(s) form a listed public suffix (not only the "*" default rule). */
    public boolean hasKnownSuffix(String host) {
        String s = publicSuffix(host);
        return rules.contains(s) || wildcards.contains(s.substring(s.indexOf('.') + 1));
    }

    /** "login.flipkart.com" in front of the real domain -> "flipkart.com"; null when nothing domain-like is embedded. */
    private String embeddedDomain(String sub) {
        if (sub.isEmpty() || !sub.contains(".")) return null;
        // look at every "x.<suffix>" pair inside the sub-domain, e.g. "secure.sbi.co.in.verify" -> "sbi.co.in"
        String[] p = sub.split("\\.");
        for (int end = p.length; end >= 2; end--) {
            String cand = String.join(".", java.util.Arrays.copyOfRange(p, 0, end));
            String suf = publicSuffix(cand);
            if (suf.equals(cand) || !rules.contains(suf)) continue; // needs a real suffix such as "com" / "co.in"
            String rest = cand.substring(0, cand.length() - suf.length() - 1);
            return rest.substring(rest.lastIndexOf('.') + 1) + "." + suf;
        }
        return null;
    }

    private static boolean mixedScript(String label) {
        return LATIN.matcher(label).find() && NON_LATIN_LETTER.matcher(label).find();
    }

    private static String extractHost(String s) {
        int scheme = s.indexOf("://");
        String rest = scheme >= 0 && scheme < 12 ? s.substring(scheme + 3) : s;
        rest = rest.replace('\\', '/');
        int cut = rest.length();
        for (char c : new char[]{'/', '?', '#'}) { int i = rest.indexOf(c); if (i >= 0 && i < cut) cut = i; }
        String auth = rest.substring(0, cut);
        int at = auth.lastIndexOf('@');
        if (at >= 0) auth = auth.substring(at + 1);              // "paytm.com@evil.xyz" -> evil.xyz
        if (auth.startsWith("[")) { int e = auth.indexOf(']'); return e > 0 ? auth.substring(0, e + 1) : auth; }
        int colon = auth.lastIndexOf(':');
        if (colon >= 0 && auth.indexOf(':') == colon) auth = auth.substring(0, colon); // port
        return auth.strip();
    }
}
