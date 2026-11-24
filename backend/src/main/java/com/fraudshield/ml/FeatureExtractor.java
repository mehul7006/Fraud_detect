package com.fraudshield.ml;

import com.fraudshield.lookalike.LookalikeMatcher.Kind;
import com.fraudshield.lookalike.LookalikeService;
import com.fraudshield.lookalike.NormalizedDomain;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 11.2: the link model's input - numbers computed from the address only (no network), so training data and live
 * scans see exactly the same thing. This class is the ONLY implementation: {@code ml/train.py} trains on the CSV this
 * class writes ({@code FeatureDumpTool}), so training and serving cannot drift apart. Changing a feature = new
 * {@link #VERSION}, new test vectors ({@code ml/feature-vectors.csv}), retrained model.
 *
 * <p>Deliberately NOT a feature: "is the site popular". In the training data every benign site is from the Tranco
 * list, so the model would learn "unknown = phishing". Popular / official sites are handled by policy (11.5) instead.
 */
public final class FeatureExtractor {
    public static final int VERSION = 2;
    /** Hashed letter-triples of host + path, and hashed words of path + query (bag-of-n-grams, a strong URL signal). */
    static final int GRAM_BUCKETS = 256, WORD_BUCKETS = 128;

    private static final List<String> BASE = List.of(
            "url_len", "host_len", "path_len", "query_len", "host_dots", "host_hyphens", "host_digits", "url_digit_ratio",
            "subdomain_depth", "is_ip", "is_https", "has_port", "has_at", "query_params", "path_depth", "host_entropy",
            "path_entropy", "tld_risky", "tld_country", "tld_len", "shortener", "free_host", "idn", "mixed_script",
            "bait_words", "sensitive_path_words", "page_ext", "apk", "double_slash_path", "embedded_url",
            "lookalike_score", "lk_homoglyph", "lk_wrong_tld", "lk_typo", "lk_combo", "lk_subdomain", "lk_path",
            "embedded_domain", "longest_host_token", "label_vowel_ratio", "label_max_consonants", "long_random_token",
            "label_len", "special_chars");

    public static final List<String> NAMES = names();

    private static List<String> names() {
        List<String> n = new ArrayList<>(BASE);
        for (int i = 0; i < GRAM_BUCKETS; i++) n.add(String.format(Locale.ROOT, "g%03d", i));
        for (int i = 0; i < WORD_BUCKETS; i++) n.add(String.format(Locale.ROOT, "w%03d", i));
        return List.copyOf(n);
    }

    private static final Set<String> RISKY_TLDS = Set.of("xyz", "top", "click", "icu", "gq", "tk", "ml", "cf", "ga",
            "zip", "mov", "buzz", "monster", "rest", "cfd", "sbs", "cyou", "shop", "online", "site", "live", "info",
            "support", "help", "work", "fun", "life", "vip", "pw", "cc", "su", "ws");
    private static final Set<String> SHORTENERS = Set.of("bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly",
            "rb.gy", "shorturl.at", "tiny.cc", "ow.ly", "buff.ly", "rebrand.ly", "t.ly", "s.id", "v.gd");
    /** Free website builders / hosting: anyone gets "name.<this>" in a minute. */
    private static final List<String> FREE_HOSTS = List.of("netlify.app", "vercel.app", "web.app", "firebaseapp.com",
            "github.io", "pages.dev", "workers.dev", "weebly.com", "wixsite.com", "blogspot.com", "000webhostapp.com",
            "glitch.me", "herokuapp.com", "onrender.com", "repl.co", "framer.app", "webflow.io", "square.site",
            "godaddysites.com", "sites.google.com", "forms.gle", "ngrok.io", "ngrok-free.app", "azurewebsites.net",
            "r2.dev", "s3.amazonaws.com", "storage.googleapis.com", "ipfs.io", "surge.sh", "carrd.co", "mystrikingly.com",
            "translate.goog", "duckdns.org", "replit.app", "gitbook.io", "bit.ly");
    private static final List<String> BAIT = List.of("login", "verify", "update", "free", "gift", "prize", "winner",
            "reward", "kyc", "suspend", "locked", "claim", "refund", "otp", "bonus", "secure", "account", "confirm");
    private static final List<String> SENSITIVE = List.of("signin", "sign-in", "logon", "wp-", "admin", "webscr",
            "banking", "wallet", "password", "auth", "session", "validate", "recover", "unlock", "billing", "payment");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern RANDOM = Pattern.compile("[A-Za-z0-9_\\-]{24,}|[a-f0-9]{16,}");

    private final LookalikeService lookalike;

    public FeatureExtractor(LookalikeService lookalike) { this.lookalike = lookalike; }

    public double[] extract(String rawUrl) {
        String url = rawUrl.strip();
        String withScheme = url.contains("://") ? url : "http://" + url;
        NormalizedDomain d = lookalike.normalize(withScheme);
        String host = d == null ? "" : d.host();
        String path = "", query = "", scheme = withScheme.substring(0, withScheme.indexOf("://")).toLowerCase(Locale.ROOT);
        boolean port = false, at = false;
        try {
            URI u = URI.create(withScheme.replace(" ", "%20").replace("|", "%7C"));
            path = u.getRawPath() == null ? "" : u.getRawPath();
            query = u.getRawQuery() == null ? "" : u.getRawQuery();
            port = u.getPort() > 0;
            at = u.getRawUserInfo() != null;
        } catch (IllegalArgumentException e) {
            int s = withScheme.indexOf('/', withScheme.indexOf("://") + 3);
            if (s > 0) {
                String rest = withScheme.substring(s);
                int q = rest.indexOf('?');
                path = q >= 0 ? rest.substring(0, q) : rest;
                query = q >= 0 ? rest.substring(q + 1) : "";
            }
        }
        String authority = withScheme.substring(withScheme.indexOf("://") + 3).split("[/?#]", 2)[0];
        at = at || authority.contains("@");
        String lower = url.toLowerCase(Locale.ROOT);
        String suffix = d == null ? "" : d.publicSuffix();
        String tld = suffix.substring(suffix.lastIndexOf('.') + 1);
        String label = d == null ? "" : d.label();
        boolean ip = d != null && d.ip() || IPV4.matcher(host).matches();

        var m = d == null ? Optional.<com.fraudshield.lookalike.LookalikeMatcher.Match>empty() : lookalike.check(d, path);
        Kind k = m.map(x -> x.kind()).orElse(null);

        double[] f = new double[NAMES.size()];
        int i = 0;
        f[i++] = Math.min(url.length(), 500);
        f[i++] = host.length();
        f[i++] = Math.min(path.length(), 400);
        f[i++] = Math.min(query.length(), 400);
        f[i++] = count(host, '.');
        f[i++] = count(host, '-');
        f[i++] = digits(host);
        f[i++] = url.isEmpty() ? 0 : round(digits(url) / (double) url.length());
        f[i++] = d == null ? 0 : d.subdomainDepth();
        f[i++] = ip ? 1 : 0;
        f[i++] = scheme.equals("https") ? 1 : 0;
        f[i++] = port ? 1 : 0;
        f[i++] = at ? 1 : 0;
        f[i++] = query.isEmpty() ? 0 : query.split("&").length;
        f[i++] = path.isEmpty() || path.equals("/") ? 0 : path.replaceAll("/+$", "").split("/").length - 1;
        f[i++] = entropy(host);
        f[i++] = entropy(path);
        f[i++] = !ip && RISKY_TLDS.contains(tld) ? 1 : 0;
        f[i++] = !ip && tld.length() == 2 ? 1 : 0;
        f[i++] = ip ? 0 : tld.length();
        f[i++] = d != null && (SHORTENERS.contains(d.registrable()) || SHORTENERS.contains(host)) ? 1 : 0;
        f[i++] = freeHost(host) ? 1 : 0;
        f[i++] = d != null && d.idn() ? 1 : 0;
        f[i++] = d != null && d.mixedScript() ? 1 : 0;
        f[i++] = BAIT.stream().filter(lower::contains).count();
        String lp = path.toLowerCase(Locale.ROOT) + "?" + query.toLowerCase(Locale.ROOT);
        f[i++] = SENSITIVE.stream().filter(lp::contains).count();
        f[i++] = lp.matches(".*\\.(php|html?|aspx?|jsp|cgi)(\\?.*)?$") ? 1 : 0;
        f[i++] = lp.contains(".apk") ? 1 : 0;
        f[i++] = path.contains("//") ? 1 : 0;
        f[i++] = lp.contains("http%3a") || lp.contains("http:") || lp.contains("https:") || lp.contains("www.") ? 1 : 0;
        f[i++] = m.map(x -> x.score()).orElse(0);
        for (Kind kind : new Kind[]{Kind.HOMOGLYPH, Kind.WRONG_TLD, Kind.TYPO, Kind.COMBO, Kind.SUBDOMAIN, Kind.PATH})
            f[i++] = k == kind ? 1 : 0;
        f[i++] = d != null && d.embeddedDomain() != null ? 1 : 0;
        f[i++] = Arrays.stream(host.split("[.\\-]")).mapToInt(String::length).max().orElse(0);
        f[i++] = vowelRatio(label);
        f[i++] = maxConsonants(label);
        f[i++] = RANDOM.matcher(path + "?" + query).find() ? 1 : 0;
        f[i++] = label.length();
        f[i++] = url.chars().filter(c -> "~%=&;_+!*'()$,".indexOf(c) >= 0).count();
        String text = (host + path).toLowerCase(Locale.ROOT);
        for (int a = 0; a + 3 <= text.length(); a++) f[i + bucket(text.substring(a, a + 3), GRAM_BUCKETS)]++;
        i += GRAM_BUCKETS;
        for (String wd : (path + " " + query).toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
            if (wd.length() >= 2 && wd.length() <= 30) f[i + bucket(wd, WORD_BUCKETS)]++;
        i += WORD_BUCKETS;
        if (i != f.length) throw new IllegalStateException("feature count " + i);
        return f;
    }

    /** FNV-1a (32 bit) of the UTF-8 bytes: stable across JVMs and languages. */
    static int bucket(String s, int n) {
        int h = 0x811c9dc5;
        for (byte b : s.getBytes(java.nio.charset.StandardCharsets.UTF_8)) { h ^= (b & 0xff); h *= 0x01000193; }
        return Integer.remainderUnsigned(h, n);
    }

    private static boolean freeHost(String host) {
        for (String h : FREE_HOSTS) if (host.equals(h) || host.endsWith("." + h)) return true;
        return false;
    }

    private static int count(String s, char c) { return (int) s.chars().filter(x -> x == c).count(); }

    private static int digits(String s) { return (int) s.chars().filter(Character::isDigit).count(); }

    /** Shannon entropy in bits, rounded to 4 decimals (stable across platforms). */
    static double entropy(String s) {
        if (s.isEmpty()) return 0;
        Map<Integer, Integer> c = new HashMap<>();
        s.codePoints().forEach(x -> c.merge(x, 1, Integer::sum));
        double n = s.codePointCount(0, s.length()), e = 0;
        for (int v : c.values()) { double p = v / n; e -= p * (Math.log(p) / Math.log(2)); }
        return round(e);
    }

    private static double vowelRatio(String s) {
        long letters = s.chars().filter(Character::isLetter).count();
        if (letters == 0) return 0;
        return round(s.chars().filter(x -> "aeiou".indexOf(x) >= 0).count() / (double) letters);
    }

    private static int maxConsonants(String s) {
        int best = 0, run = 0;
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c) && "aeiouy".indexOf(c) < 0) best = Math.max(best, ++run);
            else run = 0;
        }
        return best;
    }

    private static double round(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
