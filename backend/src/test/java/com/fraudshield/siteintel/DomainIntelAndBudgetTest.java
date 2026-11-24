package com.fraudshield.siteintel;

import static org.junit.jupiter.api.Assertions.*;

import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.lookalike.LookalikeService;
import com.fraudshield.repo.Repositories.BlocklistRepository;
import com.fraudshield.service.NoopReputationProvider;
import com.fraudshield.service.UrlScanService;
import com.sun.net.httpserver.HttpServer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 10.5 RDAP domain facts (stub registry) and 10.10 time budget + cache, end to end through UrlScanService. */
class DomainIntelAndBudgetTest {
    static HttpServer rdap, web;
    static String bootstrap;

    @BeforeAll
    static void start() throws Exception {
        com.fraudshield.ml.MlScoreService.defaults(); // the server loads the model at start-up, not inside a scan
        rdap = StubServers.http();
        int p = rdap.getAddress().getPort();
        String base = "http://127.0.0.1:" + p + "/rdap/";
        StubServers.reply(rdap, "/dns.json", 200, "application/json",
                "{\"services\":[[[\"test\"],[\"" + base + "\"]]]}");
        String reg3 = Instant.now().minus(Duration.ofDays(3)).toString();
        StubServers.reply(rdap, "/rdap/domain/new-shop.test", 200, "application/rdap+json",
                "{\"events\":[{\"eventAction\":\"registration\",\"eventDate\":\"" + reg3 + "\"}],\"entities\":[{\"roles\":[\"registrar\"],"
                        + "\"vcardArray\":[\"vcard\",[[\"version\",{},\"text\",\"4.0\"],[\"fn\",{},\"text\",\"Cheap Names Ltd\"]]]},"
                        + "{\"roles\":[\"registrant\"],\"vcardArray\":[\"vcard\",[[\"adr\",{\"cc\":\"IN\"},\"text\",[\"\",\"\",\"\",\"\",\"\",\"\",\"\"]]]]}]}");
        StubServers.reply(rdap, "/rdap/domain/old-shop.test", 200, "application/rdap+json",
                "{\"events\":[{\"eventAction\":\"registration\",\"eventDate\":\"2009-05-01T00:00:00Z\"}]}");
        StubServers.slow(rdap, "/rdap/domain/slow-registry.test", 5_000, "{}");
        bootstrap = "http://127.0.0.1:" + p + "/dns.json";

        web = StubServers.http();
        StubServers.reply(web, "/", 200, "text/html", "<html><title>Shop</title><body>hello</body></html>");
        StubServers.slow(web, "/slow", 6_000, "<html><title>late</title></html>");
    }

    @AfterAll
    static void stop() { rdap.stop(0); web.stop(0); }

    @Test
    void rdap_facts_and_score() {
        var svc = new DomainIntelService(bootstrap, Duration.ofMillis(1500));
        var f = svc.lookup("new-shop.test");
        assertTrue(f.known());
        assertEquals(3, f.ageDays(Instant.now()));
        assertEquals("Cheap Names Ltd", f.registrar());
        assertEquals("IN", f.country());
        assertEquals("NEW_DOMAIN", DomainIntelService.score(f, Instant.now()).orElseThrow().code());
        assertEquals(35, DomainIntelService.score(f, Instant.now()).orElseThrow().points());
        assertTrue(DomainIntelService.score(svc.lookup("old-shop.test"), Instant.now()).isEmpty(), "old domains add nothing");
        assertSame(f, svc.cached("new-shop.test"), "cached 24 h");
    }

    @Test
    void rdap_timeout_or_unknown_registry_degrades() {
        var svc = new DomainIntelService(bootstrap, Duration.ofMillis(700));
        long t0 = System.nanoTime();
        var slow = svc.lookup("slow-registry.test");
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 2_000);
        assertFalse(slow.known());
        assertFalse(svc.lookup("site.unknowntld").known());
        assertTrue(DomainIntelService.score(slow, Instant.now()).isEmpty());
    }

    private UrlScanService scanner(String... names) {
        var repo = Mockito.mock(BlocklistRepository.class);
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        var intel = new SiteIntel(LookalikeService.defaults(), new DomainIntelService(bootstrap, Duration.ofMillis(1500)),
                new SafeHttp(StubServers.loopbackGuard(names), 1000, 1 << 20, null), Duration.ofMillis(2700));
        return new UrlScanService(repo, new NoopReputationProvider(), LookalikeService.defaults(), intel, Duration.ofSeconds(3));
    }

    @Test
    void new_domain_adds_to_the_score() {
        var r = scanner("new-shop.test").scan("http://new-shop.test:" + web.getAddress().getPort() + "/");
        assertTrue(r.reasonCodes().contains("NEW_DOMAIN"), r.toString());
    }

    @Test
    void slow_site_and_slow_registry_still_answer_within_3_seconds_then_cache() {
        var scan = scanner("slow-registry.test");
        String url = "http://slow-registry.test:" + web.getAddress().getPort() + "/slow";
        long t0 = System.nanoTime();
        var r = scan.scan(url);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms <= 3_000, "took " + ms + " ms");
        assertNotNull(r.verdict());

        String fast = "http://new-shop.test:" + web.getAddress().getPort() + "/";
        var s2 = scanner("new-shop.test");
        s2.scan(fast);                                   // fills the per-host cache
        long t1 = System.nanoTime();
        var again = s2.scan(fast);
        long ms2 = (System.nanoTime() - t1) / 1_000_000;
        assertTrue(ms2 < 50, "second scan of the same host took " + ms2 + " ms");
        assertTrue(again.reasonCodes().contains("NEW_DOMAIN"), "cached facts are used");
    }

    @Test
    void official_and_popular_sites_are_not_fetched() {
        var intel = new SiteIntel(LookalikeService.defaults(), new DomainIntelService(bootstrap, Duration.ofMillis(500)),
                new SafeHttp(StubServers.loopbackGuard(), 500, 1 << 20, null), Duration.ofMillis(1000));
        assertTrue(intel.skip(LookalikeService.defaults().normalize("https://www.flipkart.com/")));
        assertTrue(intel.skip(LookalikeService.defaults().normalize("https://en.wikipedia.org/")));
        assertFalse(intel.skip(LookalikeService.defaults().normalize("https://fleepkart.com/")));
    }

    @Test
    void fake_page_behind_a_harmless_name_is_caught_by_the_page_check() throws Exception {
        byte[] body;
        try (var in = getClass().getResourceAsStream("/fixtures/fake-flipkart.html")) { body = in.readAllBytes(); }
        StubServers.reply(web, "/claim", 200, "text/html", new String(body, StandardCharsets.UTF_8));
        var r = scanner("bbd-rewards.test").scan("http://bbd-rewards.test:" + web.getAddress().getPort() + "/claim");
        assertEquals(Verdict.MALICIOUS, r.verdict(), r.toString());
        assertEquals("Flipkart", r.impersonatedBrand());
        assertEquals("https://www.flipkart.com", r.officialUrl());
        assertTrue(r.reasons().stream().anyMatch(x -> x.contains("Copies the Flipkart page")), r.reasons().toString());
    }
}
