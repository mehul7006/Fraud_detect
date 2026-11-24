package com.fraudshield.siteintel;

import static org.junit.jupiter.api.Assertions.*;

import com.fraudshield.lookalike.LookalikeService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 10.7 page features and 10.8 page-vs-brand check, on fixture pages. */
class PageCheckTest {
    static final LookalikeService L = LookalikeService.defaults();

    static byte[] fixture(String name) throws Exception {
        try (var in = PageCheckTest.class.getResourceAsStream("/fixtures/" + name)) { return in.readAllBytes(); }
    }

    private static PageBrandCheck check() { return new PageBrandCheck(L.registry(), L.normalizer()); }

    @Test
    void parses_the_fake_page() throws Exception {
        var f = PageFeatures.parse(fixture("fake-flipkart.html"), "text/html; charset=utf-8", "https://bbd-rewards.example.in/claim");
        assertTrue(f.title().startsWith("Flipkart - Big Billion Days"));
        assertEquals("https://bbd-rewards.example.in/static/fk-favicon.ico", f.faviconUrl());
        assertEquals(1, f.forms().size());
        var form = f.forms().get(0);
        assertEquals("https://collect-data.xyz/submit.php", form.actionUrl());
        assertTrue(form.password() && form.otp() && form.card(), form.toString());
        assertTrue(f.externalScriptHosts().contains("cdn.collect-data.xyz"));
        assertTrue(f.text().contains("your kyc is pending"));
    }

    @Test
    void fake_flipkart_page_on_unknown_site_is_malicious_and_says_so() throws Exception {
        var site = L.normalize("https://bbd-rewards.example.in/claim");
        var page = PageFeatures.parse(fixture("fake-flipkart.html"), "text/html", "https://bbd-rewards.example.in/claim");
        var r = check().check(site, page, null, null);
        var codes = r.findings().stream().map(Finding::code).toList();
        assertEquals("Flipkart", r.brand().name(), "brand found from the page itself");
        assertTrue(codes.containsAll(java.util.List.of("COPIES_BRAND_PAGE", "ASKS_FOR_SECRETS", "FORM_TO_OTHER_DOMAIN", "SCAM_WORDING")), codes.toString());
        assertTrue(r.findings().stream().anyMatch(x -> x.reason().contains("Copies the Flipkart page")));
        int points = r.findings().stream().mapToInt(Finding::points).sum();
        assertTrue(points >= 70, "page alone is enough for MALICIOUS: " + points);
    }

    @Test
    void the_same_page_on_the_official_site_is_not_judged() throws Exception {
        var page = PageFeatures.parse(fixture("fake-flipkart.html"), "text/html", "https://www.flipkart.com/x");
        assertTrue(check().check(L.normalize("https://www.flipkart.com/x"), page, null, null).findings().isEmpty());
    }

    @Test
    void ordinary_shop_that_mentions_a_brand_is_fine() throws Exception {
        var page = PageFeatures.parse(fixture("real-shop.html"), "text/html", "https://sharmahandicrafts.example.in/");
        var r = check().check(L.normalize("https://sharmahandicrafts.example.in/"), page, null, null);
        assertTrue(r.findings().isEmpty(), r.findings().toString());
        assertNull(r.brand());
    }

    @Test
    void hindi_page_and_meta_refresh_and_password_over_http() {
        String html = "<html><head><title>SBI YONO</title><meta http-equiv=\"refresh\" content=\"5; url=https://other-site.xyz/p\"></head>"
                + "<body>Aapka account band ho jayega. Turant update karein.<form action=\"http://sbi-yono.example.in/x\">"
                + "<input type=password name=p><input name=mpin></form></body></html>";
        var page = PageFeatures.parse(html.getBytes(StandardCharsets.UTF_8), "text/html", "http://sbi-yono.example.in/");
        var codes = check().check(L.normalize("http://sbi-yono.example.in/"), page, null, null).findings().stream().map(Finding::code).toList();
        assertTrue(codes.containsAll(java.util.List.of("COPIES_BRAND_PAGE", "ASKS_FOR_SECRETS", "SCAM_WORDING", "META_REFRESH_AWAY", "PASSWORD_OVER_HTTP")), codes.toString());
    }

    @Test
    void logo_hash_match_is_reported() throws Exception {
        var entries = new java.util.ArrayList<com.fraudshield.domain.BrandEntry>();
        for (var b : L.registry().brands()) {
            var e = new com.fraudshield.domain.BrandEntry(b.name(), b.category(), String.join("|", b.officialHosts()), String.join("|", b.keywords()), "t");
            if (b.name().equals("Paytm")) e.setFaviconHash("ab".repeat(32));
            entries.add(e);
        }
        var svc = new LookalikeService();
        svc.reload(entries);
        var page = PageFeatures.parse("<html><title>Wallet</title><body>hello</body></html>".getBytes(), "text/html", "https://wallet.example.in/");
        var r = new PageBrandCheck(svc.registry(), svc.normalizer()).check(svc.normalize("https://wallet.example.in/"), page, null, "AB".repeat(32));
        assertEquals("Paytm", r.brand().name());
        assertTrue(r.findings().stream().anyMatch(f -> f.code().equals("COPIES_BRAND_LOGO")));
    }

    @Test
    void fetcher_drops_query_and_credentials_and_reads_html() throws Exception {
        assertEquals("https://x.example/a/b", PageFetcher.withoutQuery("https://user:pw@x.example/a/b?token=123#f"));
        assertEquals("http://x.example/", PageFetcher.withoutQuery("x.example?otp=55"));
        var s = StubServers.http();
        try {
            byte[] body = fixture("fake-flipkart.html");
            StubServers.reply(s, "/claim", 200, "text/html", new String(body, StandardCharsets.UTF_8));
            var fetcher = new PageFetcher(new SafeHttp(StubServers.loopbackGuard(), 1000, 1 << 20, null));
            var p = fetcher.fetch("http://localhost:" + s.getAddress().getPort() + "/claim?session=secret", Instant.now().plusSeconds(3));
            assertNotNull(p.features());
            assertEquals("/claim", p.chain().last().url().getRawPath());
            assertNull(p.chain().last().url().getRawQuery(), "query never sent");
        } finally {
            s.stop(0);
        }
    }
}
