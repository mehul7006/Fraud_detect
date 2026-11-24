package com.fraudshield.safety;

import com.fraudshield.calls.NumberReputationService;
import com.fraudshield.calls.PhoneNumbers;
import com.fraudshield.lookalike.BrandRegistry;
import com.fraudshield.lookalike.BrandRegistry.Brand;
import com.fraudshield.lookalike.LookalikeService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 15.9: "Is this really SBI's customer care?" Fake helpline numbers (planted in search results, ads and social media)
 * are a top scam in India. A number is checked against a small directory of OFFICIAL customer-care numbers - every row
 * copied from the brand's own website, with the page it came from ({@code seed/care-numbers.csv}) - and against
 * community reports (the Phase 16 number store; short codes and toll-free numbers cannot be reported there).
 * <ul>
 *   <li>in the directory -> OFFICIAL (with the brand)</li>
 *   <li>a 10-digit mobile number given as a brand's customer care -> FAKE (companies do not use personal mobiles)</li>
 *   <li>not one of the brand's listed numbers -> SUSPICIOUS, with the official numbers to call instead</li>
 *   <li>marked SUSPICIOUS by community reports -> FAKE</li>
 * </ul>
 */
@Service
public class CareNumberService {
    public record Entry(String brand, String number, String display, String purpose, String sourceUrl) {}
    public record OfficialNumber(String number, String purpose) {}
    public record Result(String number, boolean official, String brand, String verdict, int score, List<String> reasonCodes,
                         List<String> reasons, List<String> reasonsHi, List<OfficialNumber> officialNumbers,
                         String officialUrl, String sourceUrl) {}

    static final Map<String, String[]> TEXT = Map.of(
            "OFFICIAL", new String[]{"This is an official %s customer-care number (from %s's own website).",
                    "यह %s का आधिकारिक कस्टमर-केयर नंबर है (%s की अपनी वेबसाइट से)।"},
            "PERSONAL_MOBILE_FOR_BRAND", new String[]{"This is a mobile number that is not one of %s's official numbers. Fake \"%s helplines\" almost always use mobile numbers.",
                    "यह मोबाइल नंबर %s के आधिकारिक नंबरों में नहीं है। नकली \"%s हेल्पलाइन\" लगभग हमेशा मोबाइल नंबर इस्तेमाल करती हैं।"},
            "NOT_IN_OFFICIAL_LIST", new String[]{"This is not one of the official %s numbers we know. Call only the numbers below or the one in the official %s app.",
                    "यह %s के उन आधिकारिक नंबरों में नहीं है जो हम जानते हैं। केवल नीचे दिए नंबर या आधिकारिक %s ऐप वाला नंबर ही मिलाएँ।"},
            "NO_DIRECTORY", new String[]{"We do not have %s's official numbers yet - take the number only from %s's official website or app.",
                    "हमारे पास अभी %s के आधिकारिक नंबर नहीं हैं - नंबर केवल %s की आधिकारिक वेबसाइट या ऐप से लें।"},
            "MOBILE_NUMBER", new String[]{"This is a mobile number. Real customer-care lines are almost never personal mobile numbers.",
                    "यह मोबाइल नंबर है। असली कस्टमर-केयर नंबर लगभग कभी निजी मोबाइल नंबर नहीं होते।"},
            "COMMUNITY_REPORTED", new String[]{"Several FraudShield users have reported this number as a fake customer care.",
                    "कई FraudShield उपयोगकर्ताओं ने इस नंबर को नकली कस्टमर केयर बताया है।"});

    private final Map<String, List<Entry>> byNumber = new HashMap<>();
    private final Map<String, List<Entry>> byBrand = new LinkedHashMap<>();
    private final BrandRegistry registry;
    private final Predicate<String> reported;

    @Autowired
    public CareNumberService(LookalikeService lookalike, NumberReputationService numbers) {
        this(lookalike.registry(), raw -> isReported(numbers, raw), load());
    }

    /** @param reported raw number -> the community has marked it SUSPICIOUS */
    CareNumberService(BrandRegistry registry, Predicate<String> reported, List<Entry> directory) {
        this.registry = registry; this.reported = reported;
        for (Entry e : directory) {
            byNumber.computeIfAbsent(e.number(), k -> new ArrayList<>()).add(e);
            byBrand.computeIfAbsent(e.brand(), k -> new ArrayList<>()).add(e);
        }
    }

    public Map<String, List<Entry>> directory() { return Collections.unmodifiableMap(byBrand); }

    public Result check(String rawNumber, String brandHint) {
        String n = SafetyValues.number(rawNumber == null ? "" : rawNumber);
        if (n.length() < 3 || n.length() > 13)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "not a phone number");
        List<Entry> hit = byNumber.getOrDefault(n, List.of());
        Brand claimed = brand(brandHint).orElse(null);
        if (!hit.isEmpty()) {
            Entry e = claimed == null ? hit.get(0)
                    : hit.stream().filter(x -> x.brand().equals(claimed.name())).findFirst().orElse(null);
            if (e != null) {
                return new Result(n, true, e.brand(), "OFFICIAL", 0, List.of("OFFICIAL"),
                        List.of(fmt("OFFICIAL", 0, e.brand())), List.of(fmt("OFFICIAL", 1, e.brand())),
                        officials(e.brand()), officialUrl(e.brand()), e.sourceUrl());
            }
            // official number of ANOTHER brand given as this brand's number: falls through as "not in list"
        }
        List<String> codes = new ArrayList<>(), en = new ArrayList<>(), hi = new ArrayList<>();
        int score = 0;
        if (reported.test(rawNumber)) { score += 70; add(codes, en, hi, "COMMUNITY_REPORTED"); }
        String brandName = claimed == null ? null : claimed.name();
        boolean mobile = SafetyValues.looksMobile(rawNumber);
        if (claimed != null) {
            boolean known = byBrand.containsKey(brandName);
            // a mobile number given as a brand's helpline: FAKE when we know the brand's real numbers (its own WhatsApp /
            // SMS mobiles are in the directory and matched above), otherwise SUSPICIOUS - our list may be incomplete
            if (mobile) { score += known ? 75 : 55; add(codes, en, hi, "PERSONAL_MOBILE_FOR_BRAND", brandName); }
            if (known) { score += 45; add(codes, en, hi, "NOT_IN_OFFICIAL_LIST", brandName); }
            else add(codes, en, hi, "NO_DIRECTORY", brandName);
        } else if (mobile) {
            score += 20; add(codes, en, hi, "MOBILE_NUMBER");
        }
        score = Math.min(100, score);
        String verdict = score >= 70 ? "FAKE" : score >= 40 ? "SUSPICIOUS" : "UNKNOWN";
        return new Result(n, false, brandName, verdict, score, codes, en, hi,
                brandName == null ? List.of() : officials(brandName), brandName == null ? null : officialUrl(brandName), null);
    }

    /** Reads the Phase 16 number store the privacy-preserving way: by the 5-character hash prefix, then the suffix. */
    static boolean isReported(NumberReputationService numbers, String raw) {
        String canonical = PhoneNumbers.canonical(raw);
        if (canonical == null) return false; // short codes / toll-free numbers are not in the number store
        String h = PhoneNumbers.hash(canonical);
        return numbers.range(h.substring(0, 5)).entries().stream().anyMatch(e -> e.suffix().equals(h.substring(5)));
    }

    /** Matches the brand the user typed / picked to the brand registry (name or keyword, any case). */
    Optional<Brand> brand(String hint) {
        if (hint == null || hint.isBlank()) return Optional.empty();
        String h = hint.trim().toLowerCase(Locale.ROOT);
        String compact = h.replaceAll("[^\\p{L}\\p{N}]+", "");
        for (Brand b : registry.brands())
            if (b.name().toLowerCase(Locale.ROOT).equals(h) || b.name().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "").equals(compact))
                return Optional.of(b);
        for (Brand b : registry.brands())
            if (b.keywords().contains(compact)) return Optional.of(b);
        return Optional.empty();
    }

    private List<OfficialNumber> officials(String brand) {
        return byBrand.getOrDefault(brand, List.of()).stream().map(e -> new OfficialNumber(e.display(), e.purpose())).toList();
    }

    private String officialUrl(String brand) {
        return registry.brands().stream().filter(b -> b.name().equals(brand)).findFirst().map(registry::officialUrl).orElse(null);
    }

    private static String fmt(String code, int lang, String brand) {
        return String.format(TEXT.get(code)[lang], brand, brand);
    }

    private static void add(List<String> codes, List<String> en, List<String> hi, String code, String... brand) {
        codes.add(code);
        String b = brand.length > 0 ? brand[0] : "";
        en.add(fmt(code, 0, b));
        hi.add(fmt(code, 1, b));
    }

    /** {@code seed/care-numbers.csv}: brand,number,purpose,source_url (lines starting with # are comments). */
    static List<Entry> load() {
        try (InputStream in = CareNumberService.class.getResourceAsStream("/seed/care-numbers.csv")) {
            if (in == null) throw new IllegalStateException("seed/care-numbers.csv missing");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<Entry> parse(String csv) {
        List<Entry> out = new ArrayList<>();
        for (String line : csv.split("\\R")) {
            String l = line.strip();
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("brand,")) continue;
            String[] c = l.split(",", -1);
            if (c.length < 4) throw new IllegalStateException("bad care-numbers row: " + l);
            out.add(new Entry(c[0].strip(), SafetyValues.number(c[1]), c[1].strip(), c[2].strip(), c[3].strip()));
        }
        return out;
    }
}
