package com.fraudshield.siteintel;

import com.fraudshield.lookalike.BrandRegistry;
import com.fraudshield.lookalike.BrandRegistry.Brand;
import com.fraudshield.lookalike.DomainNormalizer;
import com.fraudshield.lookalike.NormalizedDomain;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 10.8: does the page pretend to be a brand it does not belong to, and what does it ask for?
 * <ul>
 *   <li>COPIES_BRAND_PAGE - the brand's name in the title / heading text on a site that is not the brand's</li>
 *   <li>COPIES_BRAND_LOGO - the brand's favicon (by hash) on a site that is not the brand's</li>
 *   <li>ASKS_FOR_SECRETS - password / OTP / card / Aadhaar-PAN fields on such a page</li>
 *   <li>FORM_TO_OTHER_DOMAIN - a form sends what you type to a different site</li>
 *   <li>SCAM_WORDING - "your KYC is pending", "account blocked", "you have won" ...</li>
 *   <li>META_REFRESH_AWAY, PASSWORD_OVER_HTTP</li>
 * </ul>
 */
public final class PageBrandCheck {
    /** Typical Indian scam lines (English + common Hinglish). Lower case, matched on the page text. */
    static final List<Pattern> SCAM = List.of(
            "kyc (is )?(pending|expired|incomplete|update|not updated)", "update (your )?(kyc|pan|aadhaa?r)",
            "account (will be |has been |is )?(blocked|suspended|locked|deactivated|closed)", "verify your (account|identity|details)",
            "you have won", "congratulations[!,. ]+you", "claim (your )?(prize|reward|gift|cashback|refund)",
            "lucky (draw|winner)", "(electricity|power) (connection )?will be (disconnected|cut)", "pending (challan|bill|refund)",
            "income tax refund", "reward points? (are )?(expir|redeem)", "redeem (your )?(reward|points)",
            "(limited|only) (time|today)", "enter (your )?(otp|mpin|upi pin|atm pin|cvv)", "share (the )?otp",
            "aapka (account|khata) (band|block)", "turant (update|verify)", "inaam", "free recharge").stream()
            .map(Pattern::compile).toList();

    public record Result(List<Finding> findings, Brand brand) {}

    private final BrandRegistry registry;
    private final DomainNormalizer norm;

    public PageBrandCheck(BrandRegistry registry, DomainNormalizer norm) { this.registry = registry; this.norm = norm; }

    /** @param claimed brand already named by the address check (may be null) */
    public Result check(NormalizedDomain site, PageFeatures page, Brand claimed, String faviconSha256) {
        List<Finding> out = new ArrayList<>();
        if (page == null || site == null) return new Result(out, null);
        if (registry.officialBrand(site.registrable()).isPresent()) return new Result(out, null);

        Brand brand = claimed != null ? claimed : brandInPage(page);
        // the name in the title, or at the top of a page that asks for secrets ("we also sell on Flipkart" is fine)
        if (brand != null && (mentions(page.title().toLowerCase(Locale.ROOT), brand) || (mentions(start(page), brand) && page.asksSecrets()))) {
            out.add(new Finding(30, "COPIES_BRAND_PAGE", "Copies the " + brand.name() + " page on a site that is not " + brand.name()));
        }
        if (faviconSha256 != null) {
            for (var b : registry.brands()) {
                if (faviconSha256.equalsIgnoreCase(registry.faviconHash(b))) {
                    out.add(new Finding(40, "COPIES_BRAND_LOGO", "Shows the " + b.name() + " logo on a site that is not " + b.name()));
                    if (brand == null) brand = b;
                    break;
                }
            }
        }
        boolean copies = !out.isEmpty();
        if (page.asksSecrets()) {
            String what = secretsText(page);
            out.add(new Finding(copies ? 25 : 10, "ASKS_FOR_SECRETS", "Asks for your " + what));
        }
        Set<String> elsewhere = new TreeSet<>();
        for (var f : page.forms()) {
            var to = norm.normalize(f.actionUrl());
            if (to == null || to.registrable().equals(site.registrable())) continue;
            if (f.asksSecrets() || copies) elsewhere.add(to.registrable());
        }
        if (!elsewhere.isEmpty())
            out.add(new Finding(25, "FORM_TO_OTHER_DOMAIN", "What you type is sent to a different site (" + String.join(", ", elsewhere) + ")"));
        if (page.forms().stream().anyMatch(f -> f.password() && f.actionUrl().startsWith("http:")))
            out.add(new Finding(15, "PASSWORD_OVER_HTTP", "Sends your password without encryption"));

        int scam = 0;
        for (Pattern p : SCAM) if (p.matcher(page.text()).find() || p.matcher(page.title().toLowerCase(Locale.ROOT)).find()) scam++;
        if (scam > 0) out.add(new Finding(Math.min(25, 10 + 5 * scam), "SCAM_WORDING", "Uses typical scam wording (urgent KYC / blocked account / prize)"));

        if (page.metaRefreshUrl() != null) {
            var to = norm.normalize(page.metaRefreshUrl());
            if (to != null && !to.registrable().equals(site.registrable()))
                out.add(new Finding(10, "META_REFRESH_AWAY", "Page jumps on to another site (" + to.registrable() + ")"));
        }
        return new Result(out, copies ? brand : null);
    }

    private static String start(PageFeatures p) { return p.text().substring(0, Math.min(400, p.text().length())); }

    /** The brand named in the page title (strongest), else at the top of a page that asks for secrets. */
    Brand brandInPage(PageFeatures page) {
        String title = page.title().toLowerCase(Locale.ROOT);
        String start = start(page);
        Brand best = null;
        int bestScore = 0;
        for (var b : registry.brands()) {
            int s = (mentions(title, b) ? 2 : 0) + (page.asksSecrets() && mentions(start, b) ? 1 : 0);
            if (s > bestScore || (s == bestScore && s > 0 && b.name().length() > best.name().length())) { best = b; bestScore = s; }
        }
        return bestScore > 0 ? best : null;
    }

    /** Whole-word mention of the brand name or one of its keywords ("SBI", "Jio" count only as whole words). */
    static boolean mentions(String text, Brand b) {
        List<String> names = new ArrayList<>();
        names.add(b.name().toLowerCase(Locale.ROOT).replaceAll("\\s*\\(.*\\)", ""));
        for (String k : b.keywords()) { String w = k.replace("~", ""); if (w.length() >= 3) names.add(w); }
        for (String n : names) {
            if (n.length() < 3) continue;
            if (Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(n) + "([^a-z0-9]|$)").matcher(text).find()) return true;
        }
        return false;
    }

    private static String secretsText(PageFeatures p) {
        Set<String> w = new LinkedHashSet<>();
        for (var f : p.forms()) {
            if (f.password()) w.add("password");
            if (f.otp()) w.add("OTP / PIN");
            if (f.card()) w.add("card details");
            if (f.idNumber()) w.add("Aadhaar / PAN / account number");
        }
        return String.join(", ", w);
    }
}
