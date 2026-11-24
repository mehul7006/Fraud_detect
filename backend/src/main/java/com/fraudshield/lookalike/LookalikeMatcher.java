package com.fraudshield.lookalike;

import com.fraudshield.lookalike.BrandRegistry.Brand;
import java.text.Normalizer;
import java.util.*;

/**
 * 10.3: "is this domain a fake copy of a real brand's domain?" Pure and thread-safe.
 *
 * <p>Checks, strongest first (each gives a score and a plain-language reason):
 * <ul>
 *   <li>HOMOGLYPH - looks the same: {@code f1ipkart}, {@code rnyntra}, Cyrillic {@code аmazon}</li>
 *   <li>WRONG_TLD - the exact name with another ending: {@code flipkart.shop}</li>
 *   <li>TYPO - one or two letters off: {@code flipkarrt}, {@code fleepkart}, {@code amazno}</li>
 *   <li>COMBO - name + bait word: {@code flipkart-sale}, {@code sbikyc}, {@code jio-recharge-offer}</li>
 *   <li>SUBDOMAIN - real name / domain in front of another domain: {@code flipkart.com.evil.xyz}</li>
 *   <li>PATH - name only in the path: {@code evil.xyz/flipkart/login} (weak, needs other signals)</li>
 * </ul>
 * Never flagged: official domains of any brand, restricted registries (gov.in, bank.in...), and for the fuzzy checks,
 * popular domains (10.4).
 */
public final class LookalikeMatcher {
    public enum Kind { HOMOGLYPH, WRONG_TLD, TYPO, COMBO, SUBDOMAIN, PATH }

    /** {@code seen} = the part of the link that imitates, {@code token} = the brand name it imitates. */
    public record Match(Brand brand, String officialUrl, int score, Kind kind, String seen, String token, String reason) {}

    /** Words scammers glue to a brand name. Also short helper words ("my", "app", "india"). */
    static final Set<String> COMBO_WORDS = Set.of(
            "login", "signin", "logon", "verify", "verification", "update", "secure", "security", "account", "accounts",
            "support", "help", "helpline", "care", "customer", "customercare", "service", "services", "online", "web",
            "official", "offer", "offers", "sale", "sales", "deal", "deals", "discount", "free", "gift", "gifts", "prize",
            "reward", "rewards", "bonus", "cashback", "refund", "claim", "winner", "win", "lucky", "lottery", "kyc",
            "ekyc", "pan", "aadhaar", "otp", "pay", "payment", "payments", "bank", "banking", "netbanking", "card",
            "cards", "credit", "loan", "loans", "upi", "wallet", "recharge", "bill", "bills", "points", "redeem",
            "app", "apk", "download", "india", "in", "my", "get", "new", "real", "go", "e", "i", "m", "id",
            "portal", "center", "centre", "team", "alert", "alerts", "block", "blocked", "unblock", "suspend",
            "suspended", "locked", "unlock", "reactivate", "activate", "activation", "confirm", "validation", "check",
            "status", "track", "tracking", "delivery", "parcel", "courier", "shipment", "order", "orders", "shop",
            "shopping", "store", "mall", "festival", "diwali", "holi", "big", "billion", "days", "mega", "flash",
            "jobs", "job", "career", "careers", "hiring", "work", "invest", "investment", "trading", "crypto", "tax",
            "challan", "fine", "pending", "due", "electricity", "gas", "connection", "subsidy", "scheme", "yojana",
            "registration", "register", "apply", "form", "survey", "contest", "quiz", "spin", "game", "premium", "vip",
            "plus", "pro", "net", "site", "page", "link", "info", "now", "today", "24x7", "247", "hub", "zone",
            "point", "co", "com", "org", "mobile", "digital", "auth", "sso", "session", "mail", "email", "chat", "billing", "subscription",
            "renew", "renewal", "membership", "invoice", "kyc-update", "reward-points", "helpdesk", "contact");
    public static final Set<String> SHORTENERS = Set.of("bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "rb.gy",
            "shorturl.at", "tiny.cc", "ow.ly", "buff.ly", "rebrand.ly", "t.ly", "s.id", "v.gd", "shorturl.asia", "tinycc.com");
    /**
     * CDN edge zones: names are assigned by the CDN to its customers (big brands' own traffic runs through them -
     * google.fastly-edge.com, flipkart.com.edgekey.net). Not free hosting: never judged as look-alikes.
     * Found on a real phone: Google Play's Safe Browsing relay was blocked as "fake Google" before this list.
     */
    static final Set<String> CDN_ZONES = Set.of("fastly-edge.com", "fastly.net", "fastlylb.net", "akamai.net", "akamaiedge.net",
            "akamaized.net", "akamaihd.net", "edgekey.net", "edgesuite.net", "akamaitechnologies.com", "cloudfront.net",
            "azureedge.net", "azurefd.net", "trafficmanager.net", "cdn77.org", "b-cdn.net", "llnwd.net", "edgecastcdn.net",
            "cdn.cloudflare.net", "gvt1.com", "gvt2.com", "1e100.net");

    /** True for a host inside a CDN edge zone. */
    public static boolean cdnZone(NormalizedDomain d) {
        if (d == null) return false;
        for (String z : CDN_ZONES) if (d.host().equals(z) || d.host().endsWith("." + z)) return true;
        return false;
    }

    /** Registries only real owners can buy from: never a look-alike. */
    private static final Set<String> RESTRICTED = Set.of("gov.in", "nic.in", "bank.in", "ac.in", "edu.in", "mil.in",
            "res.in", "ernet.in", "gov", "mil", "edu", "gov.uk", "int");

    /** {@code fuzzy}: typo checks allowed - only for a brand's real registered name (or a keyword marked "~"). */
    private record Token(String text, String skeleton, Brand brand, boolean fuzzy) {}
    /** Country endings scammers like; on other country endings the same name is often the brand's own local site. */
    private static final Set<String> RISKY_CC = Set.of("co", "cc", "tk", "ml", "ga", "cf", "gq", "pw", "ws", "su", "ly",
            "to", "in", "co.in", "net.in", "org.in", "com.in", "firm.in", "gen.in", "ind.in", "biz.in", "info.in");

    private final BrandRegistry registry;
    private final PopularDomains popular;
    private final List<Token> tokens = new ArrayList<>();

    public LookalikeMatcher(BrandRegistry registry, PopularDomains popular) {
        this.registry = registry; this.popular = popular;
        for (Brand b : registry.brands()) {
            Set<String> labels = registry.officialLabels(b);
            for (String k : b.keywords()) {
                String t = k.replaceAll("[^a-z0-9]", "");
                if (t.length() >= 3) tokens.add(new Token(t, skeleton(t), b, k.startsWith("~") || labels.contains(t)));
            }
        }
    }

    public Optional<Match> check(NormalizedDomain d, String path) {
        if (d == null || d.ip() || d.label().isEmpty()) return Optional.empty();
        if (registry.officialBrand(d.registrable()).isPresent() || restricted(d.publicSuffix()) || cdnZone(d)) return Optional.empty();
        boolean isPopular = popular.contains(d.registrable()) || popular.contains(d.host());
        Match best = null;

        // 1. the domain name itself
        if (!isPopular) {
            String label = d.label();
            String whole = label.replaceAll("[-_]", "");
            String wholeSkel = skeleton(whole);
            boolean strange = d.idn() || d.mixedScript();
            for (Token t : tokens) {
                Match m = null;
                if (label.equals(t.text))
                    m = match(t, wrongTldScore(d.publicSuffix()), Kind.WRONG_TLD, label, "Uses the name \"" + t.text + "\" with a different ending (." + d.publicSuffix() + ")");
                else if (whole.equals(t.text))
                    m = match(t, 85, Kind.COMBO, label, "Splits the name \"" + t.text + "\" to look real");
                else if (wholeSkel.equals(t.skeleton) && (t.text.length() >= 5 || strange || whole.matches(".*\\d.*")))
                    m = match(t, strange ? 95 : 90, Kind.HOMOGLYPH, label, strange
                            ? "Uses letters from another alphabet to look like \"" + t.text + "\""
                            : "Swaps look-alike letters or digits to look like \"" + t.text + "\"");
                else {
                    int dist = t.fuzzy ? typoDistance(whole, t.text) : 0;
                    // short names sit close to ordinary words (agoda / agora): a typo alone is only "suspicious" there
                    int score = t.text.length() < 8 ? 65 : dist == 1 ? 85 : 80;
                    if (dist > 0) m = match(t, score, Kind.TYPO, label, "Misspells \"" + t.text + "\" by " + dist + (dist == 1 ? " letter" : " letters"));
                    else m = combo(t, label, whole);
                }
                best = better(best, m);
            }
        }

        // 2. a real domain or brand name in front of another domain
        if (d.embeddedDomain() != null) {
            var b = registry.officialBrand(d.embeddedDomain());
            if (b.isPresent()) best = better(best, new Match(b.get(), registry.officialUrl(b.get()), 90, Kind.SUBDOMAIN,
                    d.embeddedDomain(), d.embeddedDomain(), "Puts \"" + d.embeddedDomain() + "\" in front of a different site (" + d.registrable() + ")"));
        }
        if (!d.subdomain().isEmpty()) {
            List<String> parts = Arrays.stream(d.subdomain().split("[.\\-_]")).filter(s -> !s.isEmpty()).toList();
            boolean bait = parts.stream().anyMatch(COMBO_WORDS::contains);
            for (Token t : tokens)
                for (String p : parts)
                    if (p.equals(t.text) || (p.length() >= 5 && skeleton(p).equals(t.skeleton))) {
                        if (isPopular && !bait) continue; // e.g. sbi.zendesk.com may be a real support portal
                        best = better(best, match(t, isPopular ? 75 : 85, Kind.SUBDOMAIN, p,
                                "Uses the name \"" + t.text + "\" in front of a different site (" + d.registrable() + ")"));
                    }
        }

        // 3. weak: the brand name only in the path of an unknown site - or in a short link's alias, which the
        //    scammer chose ("tinyurl.com/indiapost-hold")
        boolean shortener = SHORTENERS.contains(d.registrable());
        if (best == null && (!isPopular || shortener) && path != null && !path.isBlank()) {
            Set<String> segs = new HashSet<>(Arrays.asList(path.toLowerCase(Locale.ROOT).split("[/\\-_.?=&#+%]")));
            for (Token t : tokens)
                if (t.text.length() >= 4 && segs.contains(t.text)) {
                    best = better(best, match(t, 40, Kind.PATH, t.text, "Mentions \"" + t.text + "\" on a site that is not " + t.brand.name()));
                }
        }
        return Optional.ofNullable(best);
    }

    private Match combo(Token t, String label, String whole) {
        // hyphen parts: flipkart-sale, jio-recharge-offer
        String[] parts = label.split("[-_]");
        if (parts.length > 1) {
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                boolean hit = p.equals(t.text) || (p.length() >= 5 && skeleton(p).equals(t.skeleton))
                        || (t.fuzzy && t.text.length() >= 8 && typoDistance(p, t.text) == 1);
                if (!hit) continue;
                boolean bait = false;
                for (int j = 0; j < parts.length; j++) // bait word, number, or a second name of the same brand
                    if (j != i && (isComboWords(parts[j]) || parts[j].matches("\\d+") || t.brand.keywords().contains(parts[j]))) bait = true;
                return match(t, bait ? 85 : 60, Kind.COMBO, label, bait
                        ? "Adds words like \"" + String.join("-", others(parts, i)) + "\" to the name \"" + t.text + "\""
                        : "Uses the name \"" + t.text + "\" in a different site's address");
            }
        }
        // glued: sbikyc, myflipkart, flipkartbigsale
        int idx = whole.indexOf(t.text);
        if (idx < 0 || whole.length() == t.text.length()) return null;
        String pre = whole.substring(0, idx), post = whole.substring(idx + t.text.length());
        // a tiny word may stand in front ("myflipkart", "eflipkart") but not behind ("upgrad" + "e" = "upgrade")
        boolean bait = (pre.isEmpty() || isComboWords(pre))
                && (post.isEmpty() || post.matches("\\d+") || post.equals("in") || (post.length() >= 3 && isComboWords(post)));
        if (bait) return match(t, 85, Kind.COMBO, label, "Adds \"" + (pre + " " + post).strip() + "\" to the name \"" + t.text + "\"");
        if (t.text.length() >= 7 && (pre.isEmpty() || post.isEmpty()))
            return match(t, 60, Kind.COMBO, label, "Uses the name \"" + t.text + "\" in a different site's address");
        return null;
    }

    /**
     * True when s is one combo word, or two / three glued words of 3+ letters ("bigsale" = big + sale). Tiny words
     * only count alone - otherwise "co" + "in" would make "coin" look like bait.
     */
    static boolean isComboWords(String s) {
        if (s.isEmpty()) return false;
        if (COMBO_WORDS.contains(s)) return true;
        for (int i = 3; i <= s.length() - 3; i++) {
            if (!word3(s.substring(0, i))) continue;
            String r = s.substring(i);
            if (word3(r)) return true;
            for (int j = 3; j <= r.length() - 3; j++)
                if (word3(r.substring(0, j)) && word3(r.substring(j))) return true;
        }
        return false;
    }

    private static boolean word3(String w) { return w.length() >= 3 && COMBO_WORDS.contains(w); }

    private static List<String> others(String[] parts, int skip) {
        List<String> o = new ArrayList<>();
        for (int j = 0; j < parts.length; j++) if (j != skip) o.add(parts[j]);
        return o;
    }

    private Match match(Token t, int score, Kind k, String seen, String reason) {
        return new Match(t.brand, registry.officialUrl(t.brand), score, k, seen, t.text, reason);
    }

    private static Match better(Match a, Match b) {
        if (b == null) return a;
        if (a == null) return b;
        if (b.score() != a.score()) return b.score() > a.score() ? b : a;
        return b.token().length() > a.token().length() ? b : a;
    }

    /** Same name, other ending: generic / scam-favoured endings are clear fakes; other country endings only suspicious. */
    static int wrongTldScore(String suffix) {
        String last = suffix.substring(suffix.lastIndexOf('.') + 1);
        boolean country = last.length() == 2;
        return !country || RISKY_CC.contains(suffix) || RISKY_CC.contains(last) ? 85 : 60;
    }

    /** gov.in, bank.in, ac.in ... - only real owners can register there. */
    public static boolean restricted(String suffix) {
        for (String r : RESTRICTED) if (suffix.equals(r) || suffix.endsWith("." + r)) return true;
        return false;
    }

    /**
     * 0 = not a typo of the token. The first letter must stay (people read it first; "tomato" is not "zomato").
     * One edit must be a typical slip - neighbouring key, swapped pair, doubled letter, dropped letter (5-7 letter
     * names: only a dropped double letter, so "dart" is not "dmart") - so "apply" is not "apple". Long names (8+) also
     * allow 2 edits when it still sounds the same (same consonants: "fleepkart"). Names under 5 letters: never.
     */
    static int typoDistance(String s, String t) {
        if (t.length() < 5 || s.isEmpty() || Math.abs(s.length() - t.length()) > 2) return 0;
        String ss = skeleton(s), ts = skeleton(t);
        if (ss.isEmpty() || ss.charAt(0) != ts.charAt(0)) return 0;
        int dist = Math.min(dl(s, t), dl(ss, ts));
        if (dist == 0) return 0;
        boolean longName = t.length() >= 8;
        if (dist == 1) return typicalSlip(s, t, longName) || typicalSlip(ss, ts, longName) ? 1 : 0;
        return longName && dist == 2 && consonants(ss).equals(consonants(ts)) ? 2 : 0;
    }

    private static String consonants(String s) { return s.replaceAll("[aeiouy]", "").replaceAll("(.)\\1+", "$1"); }

    private static boolean typicalSlip(String s, String t, boolean anyDrop) {
        if (s.length() == t.length()) {
            int i = 0;
            while (i < s.length() && s.charAt(i) == t.charAt(i)) i++;
            if (i + 1 < s.length() && s.charAt(i) == t.charAt(i + 1) && s.charAt(i + 1) == t.charAt(i)
                    && s.substring(i + 2).equals(t.substring(i + 2))) return i > 0; // swapped pair (not the first letter)
            return i > 0 && s.substring(i + 1).equals(t.substring(i + 1)) && Keyboard.neighbours(s.charAt(i), t.charAt(i));
        }
        String longer = s.length() > t.length() ? s : t, shorter = s.length() > t.length() ? t : s;
        int i = 0;
        while (i < shorter.length() && longer.charAt(i) == shorter.charAt(i)) i++;
        if (!longer.substring(i + 1).equals(shorter.substring(i))) return false;
        if (i == 0) return false; // first letter changed: usually a different word
        char extra = longer.charAt(i);
        if (longer == t) return anyDrop || extra == longer.charAt(i - 1)        // dropped letter: whatsap, swigy
                || (i + 1 < longer.length() && extra == longer.charAt(i + 1));
        return extra == longer.charAt(i - 1)                            // doubled letter: amazzon
                || (i + 1 < longer.length() && extra == longer.charAt(i + 1))
                || Keyboard.neighbours(extra, longer.charAt(i - 1));    // fat finger: amazpon
    }

    /** Damerau-Levenshtein (optimal string alignment). */
    static int dl(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++)
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
            }
        return d[a.length()][b.length()];
    }

    private static final Map<Character, Character> CONFUSABLE = new HashMap<>();
    static {
        String[][] m = {
            // Cyrillic
            {"а", "a"}, {"е", "e"}, {"ё", "e"}, {"о", "o"}, {"р", "p"}, {"с", "c"}, {"у", "y"}, {"х", "x"}, {"к", "k"},
            {"м", "m"}, {"і", "i"}, {"ї", "i"}, {"ј", "j"}, {"ѕ", "s"}, {"ԁ", "d"}, {"һ", "h"}, {"ӏ", "l"}, {"ԛ", "q"},
            {"ԝ", "w"}, {"ʏ", "y"}, {"ɡ", "g"}, {"п", "n"}, {"г", "r"},
            // Greek
            {"α", "a"}, {"ο", "o"}, {"ρ", "p"}, {"ν", "v"}, {"ι", "i"}, {"κ", "k"}, {"τ", "t"}, {"υ", "u"}, {"χ", "x"},
            {"ε", "e"}, {"η", "n"}, {"ω", "w"}, {"β", "b"},
            // Armenian / others
            {"ո", "n"}, {"ս", "u"}, {"օ", "o"}, {"ı", "i"}, {"ł", "l"}, {"ø", "o"}, {"đ", "d"},
            // digits and symbols
            {"0", "o"}, {"1", "l"}, {"3", "e"}, {"4", "a"}, {"5", "s"}, {"7", "t"}, {"8", "b"}, {"9", "g"}, {"2", "z"},
            {"|", "l"}, {"!", "l"}, {"$", "s"}, {"@", "a"},
        };
        for (String[] p : m) CONFUSABLE.put(p[0].charAt(0), p[1].charAt(0));
    }

    /** What the name looks like to a quick reader: accents dropped, look-alike letters folded, i/l/1 same, rn = m. */
    static String skeleton(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        StringBuilder b = new StringBuilder(n.length());
        for (char c : n.toCharArray()) {
            char x = CONFUSABLE.getOrDefault(c, c);
            if (x == 'i') x = 'l';
            if (x != '-' && x != '_' && x != '.') b.append(x);
        }
        return b.toString().replace("rn", "m").replace("vv", "w");
    }

    /** QWERTY neighbours, for fat-finger typos. */
    static final class Keyboard {
        private static final String[] ROWS = {"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm"};
        static boolean neighbours(char a, char b) {
            int[] pa = pos(a), pb = pos(b);
            if (pa == null || pb == null) return false;
            int dr = pb[0] - pa[0], dc = pb[1] - pa[1];
            if (dr == 0) return Math.abs(dc) == 1;
            if (dr == 1) return dc == 0 || dc == -1;  // row below is shifted right by half a key
            if (dr == -1) return dc == 0 || dc == 1;
            return false;
        }
        private static int[] pos(char c) {
            for (int r = 0; r < ROWS.length; r++) { int i = ROWS[r].indexOf(c); if (i >= 0) return new int[]{r, i}; }
            return null;
        }
    }
}
