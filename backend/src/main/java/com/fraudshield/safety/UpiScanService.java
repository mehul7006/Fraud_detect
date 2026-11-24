package com.fraudshield.safety;

import com.fraudshield.dto.Dtos.ScanResponse;
import com.fraudshield.lookalike.BrandRegistry.Brand;
import com.fraudshield.lookalike.LookalikeService;
import com.fraudshield.safety.CrowdItem.Status;
import com.fraudshield.safety.SafetyRepositories.UpiSeenRepository;
import com.fraudshield.service.UrlScanService;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 15.5: check a UPI ID or a scanned QR code BEFORE paying. Nothing is stored about the user; the UPI ID is kept only
 * as a hash with "first seen" time (for the "new UPI ID" hint), never logged.
 * <p>Checks: valid UPI ID format, known UPI app / bank handle, community reports (15.7), payee name claims a brand
 * but the ID is a personal one, scam words in the name / note, "scan to receive money" trick (scanning a QR always
 * SENDS money), collect request (asks money FROM you), new to FraudShield (only adds to other signs).
 * A QR that holds a web link is checked with the normal link pipeline.
 */
@Service
public class UpiScanService {
    public enum Kind { VPA, PAY, COLLECT, MANDATE, URL, INVALID }
    public record Payee(String vpa, String name, String amount, boolean handleKnown, String handleOwner) {}
    public record Result(String verdict, int score, Kind kind, List<String> reasonCodes, List<String> reasons,
                         List<String> reasonsHi, String claimedBrand, Payee payee) {}

    static final class Codes {
        static final String INVALID_VPA = "INVALID_VPA", INVALID_QR = "INVALID_QR", UNKNOWN_HANDLE = "UNKNOWN_HANDLE",
                COMMUNITY_REPORTED = "COMMUNITY_REPORTED", COMMUNITY_CONFIRMED = "COMMUNITY_CONFIRMED",
                BRAND_NAME_MISMATCH = "BRAND_NAME_MISMATCH", SCAM_WORDS = "SCAM_WORDS",
                RECEIVE_MONEY_TRICK = "RECEIVE_MONEY_TRICK", COLLECT_REQUEST = "COLLECT_REQUEST",
                AUTOPAY_MANDATE = "AUTOPAY_MANDATE", NEW_VPA = "NEW_VPA", HIGH_AMOUNT = "HIGH_AMOUNT", LINK_QR = "LINK_QR";
    }

    /** code -> {English, Hindi}; %s = brand name */
    static final Map<String, String[]> TEXT = Map.ofEntries(
            Map.entry(Codes.INVALID_VPA, new String[]{"This is not a valid UPI ID.", "यह सही UPI ID नहीं है।"}),
            Map.entry(Codes.INVALID_QR, new String[]{"This QR code is not a UPI payment code.", "यह QR कोड UPI भुगतान कोड नहीं है।"}),
            Map.entry(Codes.UNKNOWN_HANDLE, new String[]{"The part after @ is not a UPI app or bank FraudShield knows (our list is not complete - check the name shown in your UPI app).",
                    "@ के बाद वाला हिस्सा किसी जाने-पहचाने UPI ऐप या बैंक का नहीं है (हमारी सूची पूरी नहीं है - अपने UPI ऐप में दिखा नाम जाँचें)।"}),
            Map.entry(Codes.COMMUNITY_REPORTED, new String[]{"Several FraudShield users have reported this UPI ID as a scam.",
                    "कई FraudShield उपयोगकर्ताओं ने इस UPI ID को धोखा बताया है।"}),
            Map.entry(Codes.COMMUNITY_CONFIRMED, new String[]{"This UPI ID is a confirmed scam (reported by users, checked by our team).",
                    "यह UPI ID पक्का धोखा है (उपयोगकर्ताओं ने बताया, हमारी टीम ने जाँचा)।"}),
            Map.entry(Codes.BRAND_NAME_MISMATCH, new String[]{"The name says \"%s\", but this is a personal UPI ID. %s does not take payments on a personal UPI ID.",
                    "नाम \"%s\" बताता है, पर यह किसी व्यक्ति की UPI ID है। %s निजी UPI ID पर भुगतान नहीं लेता।"}),
            Map.entry(Codes.SCAM_WORDS, new String[]{"The name or note uses words common in scams (refund, prize, KYC, customer care ...).",
                    "नाम या नोट में धोखे वाले शब्द हैं (रिफंड, इनाम, KYC, कस्टमर केयर ...)।"}),
            Map.entry(Codes.RECEIVE_MONEY_TRICK, new String[]{"Scanning a QR code or entering your UPI PIN always SENDS money. You never need to do this to receive a refund or prize.",
                    "QR कोड स्कैन करने या UPI PIN डालने से पैसा हमेशा आपके खाते से जाता है। रिफंड या इनाम पाने के लिए ऐसा कभी नहीं करना पड़ता।"}),
            Map.entry(Codes.COLLECT_REQUEST, new String[]{"This is a request to TAKE money from your account, not to give you money.",
                    "यह आपके खाते से पैसा लेने का अनुरोध है, आपको पैसा देने का नहीं।"}),
            Map.entry(Codes.AUTOPAY_MANDATE, new String[]{"This sets up automatic repeated payments (AutoPay) from your account.",
                    "यह आपके खाते से बार-बार अपने-आप भुगतान (AutoPay) चालू करता है।"}),
            Map.entry(Codes.NEW_VPA, new String[]{"FraudShield has not seen this UPI ID before.", "FraudShield ने यह UPI ID पहले नहीं देखी।"}),
            Map.entry(Codes.HIGH_AMOUNT, new String[]{"A large amount is already filled in.", "पहले से बड़ी राशि भरी हुई है।"}),
            Map.entry(Codes.LINK_QR, new String[]{"This QR code opens a website, not a UPI payment.", "यह QR कोड UPI भुगतान नहीं, एक वेबसाइट खोलता है।"}));

    /** UPI handles of the big UPI apps (personal IDs) - handle -> app. */
    static final Map<String, String> APP_HANDLES = Map.ofEntries(
            Map.entry("ybl", "PhonePe"), Map.entry("ibl", "PhonePe"), Map.entry("axl", "PhonePe"),
            Map.entry("okaxis", "Google Pay"), Map.entry("okhdfcbank", "Google Pay"), Map.entry("okicici", "Google Pay"), Map.entry("oksbi", "Google Pay"),
            Map.entry("paytm", "Paytm"), Map.entry("ptyes", "Paytm"), Map.entry("ptaxis", "Paytm"), Map.entry("pthdfc", "Paytm"), Map.entry("ptsbi", "Paytm"),
            Map.entry("apl", "Amazon"), Map.entry("yapl", "Amazon"), Map.entry("rapl", "Amazon"),
            Map.entry("upi", "BHIM UPI / NPCI"), Map.entry("ikwik", "MobiKwik"),
            Map.entry("waicici", "WhatsApp"), Map.entry("waaxis", "WhatsApp"), Map.entry("wahdfcbank", "WhatsApp"), Map.entry("wasbi", "WhatsApp"));
    /** bank handles - handle -> bank (brand names as in the brand registry). */
    static final Map<String, String> BANK_HANDLES = Map.ofEntries(
            Map.entry("sbi", "State Bank of India"), Map.entry("hdfcbank", "HDFC Bank"), Map.entry("icici", "ICICI Bank"),
            Map.entry("axisbank", "Axis Bank"), Map.entry("axisb", "Axis Bank"), Map.entry("kotak", "Kotak Mahindra Bank"),
            Map.entry("kmbl", "Kotak Mahindra Bank"), Map.entry("pnb", "Punjab National Bank"), Map.entry("barodampay", "Bank of Baroda"),
            Map.entry("airtel", "Airtel Payments Bank"), Map.entry("jio", "Reliance Jio"), Map.entry("idfcbank", "IDFC FIRST Bank"),
            Map.entry("federal", "Federal Bank"), Map.entry("indus", "IndusInd Bank"), Map.entry("yesbank", "Yes Bank"),
            Map.entry("aubank", "AU Small Finance Bank"), Map.entry("cnrb", "Canara Bank"), Map.entry("unionbank", "Union Bank of India"),
            Map.entry("uboi", "Union Bank of India"));

    static final Pattern VPA = Pattern.compile("^[a-z0-9][a-z0-9._-]{1,255}@[a-z][a-z0-9]{1,63}$");
    static final Pattern MOBILE_IN_ID = Pattern.compile("(?<!\\d)[6-9]\\d{9}(?!\\d)");
    static final Set<String> SCAM_WORDS = Set.of("refund", "cashback", "prize", "lottery", "reward", "winner", "won", "kyc",
            "customer care", "customercare", "helpline", "helpdesk", "support", "receive", "claim", "lucky draw", "bonus",
            "electricity", "bill update", "verification", "verify", "blocked", "penalty", "fine");
    static final Set<String> RECEIVE_WORDS = Set.of("refund", "cashback", "prize", "lottery", "reward", "receive", "claim", "won", "winner", "bonus");

    private final LookalikeService brands;
    private final CrowdReportService crowd;
    private final UpiSeenRepository seen;
    private final UrlScanService urls;
    private final Clock clock;

    @Autowired
    public UpiScanService(LookalikeService brands, CrowdReportService crowd, UpiSeenRepository seen, UrlScanService urls) {
        this(brands, crowd, seen, urls, Clock.systemUTC());
    }

    UpiScanService(LookalikeService brands, CrowdReportService crowd, UpiSeenRepository seen, UrlScanService urls, Clock clock) {
        this.brands = brands; this.crowd = crowd; this.seen = seen; this.urls = urls; this.clock = clock;
    }

    public Result scanVpa(String vpa) { return check(Kind.VPA, vpa, null, null, null); }

    public Result scanQr(String payload) {
        String p = payload == null ? "" : payload.trim();
        String low = p.toLowerCase(Locale.ROOT);
        if (low.startsWith("http://") || low.startsWith("https://")) {
            ScanResponse s = urls.scan(p);
            List<String> codes = new ArrayList<>(List.of(Codes.LINK_QR));
            if (s.reasonCodes() != null) codes.addAll(s.reasonCodes());
            List<String> en = new ArrayList<>(List.of(TEXT.get(Codes.LINK_QR)[0]));
            if (s.reasons() != null) en.addAll(s.reasons());
            List<String> hi = new ArrayList<>(List.of(TEXT.get(Codes.LINK_QR)[1]));
            if (s.explanation() != null && s.explanation().get("hi") != null) hi.add(s.explanation().get("hi"));
            return new Result(s.verdict().name(), s.score(), Kind.URL, codes, en, hi, s.impersonatedBrand(), null);
        }
        if (!low.startsWith("upi://")) {
            Reasons r = new Reasons();
            r.add(Codes.INVALID_QR, 50);
            return r.result(Kind.INVALID, null, null);
        }
        String path = low.substring("upi://".length()).split("[?/]", 2)[0];
        Kind kind = switch (path) { case "pay" -> Kind.PAY; case "collect" -> Kind.COLLECT; case "mandate" -> Kind.MANDATE; default -> Kind.INVALID; };
        Map<String, String> q = query(p);
        if (kind == Kind.INVALID || q.get("pa") == null) {
            Reasons r = new Reasons();
            r.add(Codes.INVALID_QR, 50);
            return r.result(Kind.INVALID, null, null);
        }
        return check(kind, q.get("pa"), q.get("pn"), q.get("am"), q.get("tn"));
    }

    Result check(Kind kind, String rawVpa, String name, String amount, String note) {
        Reasons r = new Reasons();
        String vpa = rawVpa == null ? "" : rawVpa.trim().toLowerCase(Locale.ROOT);
        if (!VPA.matcher(vpa).matches()) {
            r.add(Codes.INVALID_VPA, 50);
            return r.result(kind, null, new Payee(vpa, name, amount, false, null));
        }
        String local = vpa.substring(0, vpa.indexOf('@'));
        String handle = vpa.substring(vpa.indexOf('@') + 1);
        String owner = APP_HANDLES.getOrDefault(handle, BANK_HANDLES.get(handle));
        if (owner == null) r.add(Codes.UNKNOWN_HANDLE, 20);

        String hash = SafetyValues.hash(CrowdItem.Type.UPI, vpa);
        Status st = crowd.status(CrowdItem.Type.UPI, hash);
        if (st == Status.CONFIRMED) r.add(Codes.COMMUNITY_CONFIRMED, 100);
        else if (st == Status.SUSPICIOUS) r.add(Codes.COMMUNITY_REPORTED, 70);

        String words = ((name == null ? "" : name) + " " + (note == null ? "" : note)).toLowerCase(Locale.ROOT);
        Brand claimed = claimedBrand(name, local);
        boolean personal = APP_HANDLES.containsKey(handle) || MOBILE_IN_ID.matcher(local).find();
        if (claimed != null && personal && !claimed.name().equals(owner)) r.add(Codes.BRAND_NAME_MISMATCH, 45, claimed.name());
        boolean scamWords = containsAny(words + " " + local.replaceAll("[._-]", " "), SCAM_WORDS);
        if (scamWords) r.add(Codes.SCAM_WORDS, 25);

        BigDecimal am = amount(amount);
        if (kind == Kind.PAY && containsAny(words, RECEIVE_WORDS)) r.add(Codes.RECEIVE_MONEY_TRICK, 45);
        if (kind == Kind.COLLECT) r.add(Codes.COLLECT_REQUEST, 50);
        if (kind == Kind.MANDATE) r.add(Codes.AUTOPAY_MANDATE, 30);

        boolean isNew = markSeen(hash);
        if (r.score > 0) { // these two never flag on their own
            if (isNew) r.add(Codes.NEW_VPA, 10);
            if (am != null && am.compareTo(BigDecimal.valueOf(20_000)) >= 0) r.add(Codes.HIGH_AMOUNT, 10);
        }
        return r.result(kind, claimed == null ? null : claimed.name(),
                new Payee(vpa, name, am == null ? null : am.toPlainString(), owner != null, owner));
    }

    /** Words that make a name sound like a company desk rather than a person ("SBI Customer Care", "amazon.refund"). */
    static final Set<String> BUSINESS_WORDS = Set.of("care", "customer", "customercare", "support", "help", "helpline", "helpdesk",
            "refund", "refunds", "official", "bank", "kyc", "service", "services", "office", "ltd", "limited", "pvt", "cashback",
            "reward", "rewards", "offer", "team", "desk", "tollfree", "claim", "head", "branch", "manager", "dept", "department");

    /**
     * A brand the payee pretends to be: the payee name (or the part of the UPI ID before @) is the brand itself
     * ("SBI", "amazon@ybl") or the brand plus a company word ("SBI Customer Care", "amazon.refund@ybl").
     * A brand word in a person's name alone ("Ramesh Kotak") is NOT a claim - many surnames are brand names too.
     */
    Brand claimedBrand(String name, String local) {
        Brand b = claim(name == null ? "" : name.toLowerCase(Locale.ROOT), "[^\\p{L}\\p{N}]+");
        return b != null ? b : claim(local, "[^a-z0-9]+");
    }

    private Brand claim(String text, String split) {
        String t = text.trim();
        if (t.isEmpty()) return null;
        List<String> tokens = Arrays.stream(t.split(split)).filter(s -> !s.isEmpty()).toList();
        boolean business = tokens.stream().anyMatch(BUSINESS_WORDS::contains)
                || BUSINESS_WORDS.stream().filter(w -> w.length() >= 4).anyMatch(t::contains);
        String joined = String.join("", tokens);
        for (Brand b : brands.registry().brands()) {
            String bn = b.name().toLowerCase(Locale.ROOT);
            boolean exact = joined.equals(bn.replaceAll("[^\\p{L}\\p{N}]+", ""));
            boolean mentions = (" " + String.join(" ", tokens) + " ").contains(" " + bn + " ");
            for (String k : b.keywords()) {
                if (k.length() < 3) continue;
                if (joined.equals(k)) exact = true;
                if (tokens.contains(k) || (k.length() >= 5 && t.contains(k))) mentions = true;
            }
            if (exact || (mentions && business)) return b;
        }
        return null;
    }

    /** true when FraudShield sees this (hashed) UPI ID for the first time, or first saw it less than a day ago. */
    boolean markSeen(String hash) {
        Instant now = Instant.now(clock);
        var e = seen.findById(hash);
        if (e.isPresent()) {
            UpiSeen s = e.get();
            s.seenAgain();
            seen.save(s);
            return s.getFirstSeen().isAfter(now.minus(Duration.ofDays(1)));
        }
        try { seen.saveAndFlush(new UpiSeen(hash, now)); } catch (DataIntegrityViolationException ignored) { /* same moment */ }
        return true;
    }

    static Map<String, String> query(String payload) {
        Map<String, String> out = new HashMap<>();
        int i = payload.indexOf('?');
        if (i < 0) return out;
        for (String kv : payload.substring(i + 1).split("&")) {
            int e = kv.indexOf('=');
            if (e <= 0) continue;
            try {
                out.putIfAbsent(kv.substring(0, e).toLowerCase(Locale.ROOT),
                        URLDecoder.decode(kv.substring(e + 1).replace("+", "%2B"), StandardCharsets.UTF_8).replace("%2B", "+"));
            } catch (IllegalArgumentException badEscape) {
                out.putIfAbsent(kv.substring(0, e).toLowerCase(Locale.ROOT), kv.substring(e + 1));
            }
        }
        return out;
    }

    static BigDecimal amount(String a) {
        if (a == null || a.isBlank()) return null;
        try { BigDecimal d = new BigDecimal(a.trim()); return d.signum() > 0 ? d : null; } catch (NumberFormatException e) { return null; }
    }

    private static boolean containsAny(String text, Set<String> words) {
        String t = " " + text.replaceAll("[^\\p{L}\\p{N}]+", " ") + " ";
        for (String w : words) if (t.contains(" " + w + " ")) return true;
        return false;
    }

    private static final class Reasons {
        final List<String> codes = new ArrayList<>(), en = new ArrayList<>(), hi = new ArrayList<>();
        int score;

        void add(String code, int points, String... args) {
            codes.add(code);
            String[] t = TEXT.get(code);
            Object[] a = args.length == 1 ? new Object[]{args[0], args[0]} : args;
            en.add(String.format(t[0], a));
            hi.add(String.format(t[1], a));
            score = Math.min(100, score + points);
        }

        Result result(Kind kind, String brand, Payee payee) {
            String v = score >= 70 ? "MALICIOUS" : score >= 40 ? "SUSPICIOUS" : "SAFE";
            return new Result(v, score, kind, codes, en, hi, brand, payee);
        }
    }
}
