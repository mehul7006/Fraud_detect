package com.fraudshield.lookalike;

import static org.junit.jupiter.api.Assertions.*;

import com.fraudshield.lookalike.LookalikeMatcher.Kind;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 10.1, 10.3 and 10.4. */
class LookalikeMatcherTest {
    static final DomainNormalizer N = DomainNormalizer.fromClasspath();
    static final BrandRegistry R = new BrandRegistry(BrandRegistry.seedFromClasspath(), N);
    static final LookalikeMatcher M = new LookalikeMatcher(R, PopularDomains.fromClasspath());

    static java.util.Optional<LookalikeMatcher.Match> check(String url) {
        String path;
        try { path = URI.create(url.contains("://") ? url : "http://" + url).getRawPath(); } catch (Exception e) { path = null; }
        return M.check(N.normalize(url), path);
    }

    @Test
    void registry_has_200_brands_each_with_an_official_domain() {
        assertTrue(R.size() >= 200, "brands: " + R.size());
        R.brands().forEach(b -> assertFalse(b.officialHosts().isEmpty(), b.name()));
        assertEquals("Flipkart", R.officialBrand("flipkart.com").orElseThrow().name());
        assertEquals("State Bank of India", R.officialBrand("sbi.co.in").orElseThrow().name());
        assertEquals("https://www.flipkart.com", R.officialUrl(R.officialBrand("flipkart.com").orElseThrow()));
        assertTrue(R.officialBrand("fleepkart.com").isEmpty());
    }

    /** The roadmap's examples: each is "fake Flipkart" with score >= 80. */
    @ParameterizedTest
    @ValueSource(strings = {"fleepkart.com", "flipkarrt.com", "f1ipkart.com", "flipkart-sale.xyz", "https://flipkart.shop/",
            "http://login.flipkart.com.evil.xyz/", "flipkart-big-billion-days.in", "flip-kart.com", "fIipkart.in",
            "myflipkart.top", "flipkartsale.online", "https://flipkart-offer.netlify.app/"})
    void flags_fake_flipkart(String url) {
        var m = check(url).orElseThrow(() -> new AssertionError("not flagged: " + url));
        assertEquals("Flipkart", m.brand().name(), url);
        assertTrue(m.score() >= 80, url + " score " + m.score() + " " + m.kind());
        assertEquals("https://www.flipkart.com", m.officialUrl());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "xn--mazon-3ve.com,Amazon",            // Cyrillic а
        "amazon-prize.xyz,Amazon",
        "rnyntra.com,Myntra",                  // rn = m
        "paytrn.com,Paytm",
        "phonepe-kyc.in,PhonePe",
        "sbi-kyc-update.com,State Bank of India",
        "onlinesbi-verify.co,State Bank of India",
        "sbikyc.top,State Bank of India",
        "hdfcbank-netbanking.info,HDFC Bank",
        "icicibank-reward.xyz,ICICI Bank",
        "whatsapp-web.cc,WhatsApp",
        "whatsap.com,WhatsApp",
        "instagrarn.com,Instagram",
        "faceboook.com,Facebook",
        "netflix-billing.site,Netflix",
        "jio-recharge-offer.xyz,Reliance Jio",
        "airtel-recharge.online,Airtel",
        "irctc-refund.in,IRCTC",
        "incometax-refund.in,Income Tax Department",
        "uidai-aadhaar-update.com,UIDAI (Aadhaar)",
        "echallan-parivahan.in,Parivahan",
        "indiapost-delivery.top,India Post",
        "bluedart-tracking.xyz,Blue Dart",
        "secure.sbi.co.in.verify.top,State Bank of India", // real domain hidden in front
    })
    void flags_other_brands(String url, String brand) {
        var m = check(url).orElseThrow(() -> new AssertionError("not flagged: " + url));
        assertEquals(brand, m.brand().name(), url + " " + m);
        assertTrue(m.score() >= 80, url + " score " + m.score() + " " + m.kind());
    }

    /** Short names sit near ordinary words, so a typo alone is SUSPICIOUS (60-69), not MALICIOUS. */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"amazno.in,Amazon", "zomatto.com,Zomato", "swigy.in,Swiggy", "paytn.com,Paytm", "netflx.com,Netflix"})
    void short_name_typos_are_suspicious(String url, String brand) {
        var m = check(url).orElseThrow(() -> new AssertionError("not flagged: " + url));
        assertEquals(brand, m.brand().name());
        assertEquals(Kind.TYPO, m.kind());
        assertTrue(m.score() >= 60 && m.score() < 70, url + " " + m.score());
    }

    /** Real-world false alarms found by the Tranco scan (ranks 100k-300k) - must stay below MALICIOUS. */
    @ParameterizedTest
    @ValueSource(strings = {"agora.pl", "dart.org", "gail.co.in", "coinpayu.com", "medmartonline.com", "appupgrade.dev",
            "telegraf.by", "angeloni.com.br", "bscom.ru", "umag.kz", "byus.net", "gana.com.co", "thetelegram.com",
            "imgarena.com", "payt.com.br"})
    void tranco_false_alarms_stay_below_malicious(String url) {
        var m = check(url);
        assertTrue(m.isEmpty() || m.get().score() < 70, url + " " + m.orElse(null));
    }

    /** Official sites, popular unrelated sites, restricted registries and ordinary words stay SAFE (no match). */
    @ParameterizedTest
    @ValueSource(strings = {"flipkart.com", "https://www.flipkart.com/offers", "flipkart.in", "seller.flipkart.com",
            "flickr.com", "flipboard.com", "s3.amazonaws.com", "amazon.in", "amazon.de", "pay.google.com",
            "www.onlinesbi.sbi", "sbi.co.in", "sbicard.com", "hdfc.bank.in", "uidai.gov.in", "myaadhaar.uidai.gov.in",
            "incometax.gov.in", "anything-sbi-kyc.gov.in", "icicidirect.com", "jiosaavn.com", "airtel.in",
            "tomato.com", "mantra.com", "apply.com", "applyonline.in", "pineapple.com", "google.com", "youtube.com",
            "wikipedia.org", "github.com", "stackoverflow.com", "microsoft.com", "bbc.co.uk", "ndtv.com",
            "timesofindia.indiatimes.com", "hindustantimes.com", "outlookindia.com", "unionbankph.com",
            "cybercrime-news.org", "decathlon-results.org", "http://192.168.1.1/flipkart",
            // CDN edge names seen on a real phone (12.x device test): brands' own infrastructure
            "ohttp-relay-safebrowsing-android.google.fastly-edge.com", "flipkart.com.edgekey.net", "www.paytm.com.cdn.cloudflare.net",
            "d1abc.cloudfront.net"})
    void leaves_real_and_unrelated_sites_alone(String url) {
        var m = check(url);
        assertTrue(m.isEmpty() || m.get().score() < 70, url + " wrongly flagged: " + m.orElse(null));
    }

    @Test
    void brand_only_in_path_is_weak() {
        var m = check("https://random-shop.xyz/flipkart/login").orElseThrow();
        assertEquals(Kind.PATH, m.kind());
        assertTrue(m.score() < 70);
        assertTrue(check("https://www.youtube.com/flipkart").isEmpty(), "popular site + brand in path");
        var alias = check("https://tinyurl.com/indiapost-hold").orElseThrow();
        assertEquals(Kind.PATH, alias.kind(), "short-link alias is chosen by the sender");
        assertEquals("India Post", alias.brand().name());
    }

    @Test
    void popular_platform_with_brand_and_bait_in_subdomain_is_flagged() {
        // weebly.com is popular (not a public suffix) - "sbi.weebly.com" alone could be a fan page, "+kyc" is not
        assertTrue(check("https://sbi.weebly.com").isEmpty());
        var m = check("https://sbi-kyc-update.weebly.com").orElseThrow();
        assertEquals(Kind.SUBDOMAIN, m.kind());
        assertTrue(m.score() >= 70);
    }

    @Test
    void typo_rules() {
        assertEquals(2, LookalikeMatcher.typoDistance("fleepkart", "flipkart"));
        assertEquals(1, LookalikeMatcher.typoDistance("flipkarrt", "flipkart"));
        assertEquals(0, LookalikeMatcher.typoDistance("tomato", "zomato"), "first letter changed");
        assertEquals(0, LookalikeMatcher.typoDistance("apply", "apple"), "e/y are not neighbour keys");
        assertEquals(1, LookalikeMatcher.typoDistance("paytn", "paytm"), "n is next to m");
        assertEquals(1, LookalikeMatcher.typoDistance("whatsap", "whatsapp"), "dropped double letter");
        assertEquals(1, LookalikeMatcher.typoDistance("intagram", "instagram"), "long name: any dropped letter");
        assertEquals(0, LookalikeMatcher.typoDistance("dart", "dmart"), "short name: dropped letter not doubled");
        assertEquals(0, LookalikeMatcher.typoDistance("telegraf", "telegram"), "f is not next to m");
        assertEquals(0, LookalikeMatcher.typoDistance("sbl", "sbi"), "too short");
        assertTrue(LookalikeMatcher.isComboWords("bigsale"));
        assertTrue(LookalikeMatcher.isComboWords("kycupdate"));
        assertFalse(LookalikeMatcher.isComboWords("pine"));
        assertFalse(LookalikeMatcher.isComboWords("coin"), "co + in: tiny words only count alone");
    }
}
