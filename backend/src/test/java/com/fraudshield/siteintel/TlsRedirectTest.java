package com.fraudshield.siteintel;

import static org.junit.jupiter.api.Assertions.*;

import com.fraudshield.lookalike.LookalikeService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 10.6: certificate problems and redirect chains, against local test servers. */
class TlsRedirectTest {
    static final LookalikeService L = LookalikeService.defaults();

    private static java.util.List<String> codes(java.util.List<Finding> f) { return f.stream().map(Finding::code).toList(); }

    @Test
    void certificate_for_another_site_is_detected() throws Exception {
        var s = StubServers.https("other.example");
        try {
            StubServers.reply(s, "/", 200, "text/html", "<html><title>hi</title></html>");
            var http = new SafeHttp(StubServers.loopbackGuard(), 1000, 1 << 20, StubServers.trusting("other.example", "localhost"));
            String url = "https://localhost:" + s.getAddress().getPort() + "/";
            var c = http.get(url, 5, Instant.now().plusSeconds(3));
            var tls = c.first().tls();
            assertEquals(Boolean.TRUE, tls.trusted(), "chain is fine (trusted test CA)");
            assertEquals(Boolean.FALSE, tls.hostnameMatches(), "but it is for other.example, not localhost");
            assertEquals(200, c.first().status(), "page is still read for the checks");
            var f = new TlsRedirectService(L).assess(L.normalize(url), c, Instant.now());
            assertTrue(codes(f).contains("CERT_MISMATCH"), f.toString());
        } finally {
            s.stop(0);
        }
    }

    @Test
    void matching_and_untrusted_certificates() throws Exception {
        var s = StubServers.https("localhost");
        try {
            StubServers.reply(s, "/", 200, "text/html", "ok");
            String url = "https://localhost:" + s.getAddress().getPort() + "/";
            var good = new SafeHttp(StubServers.loopbackGuard(), 1000, 1 << 20, StubServers.trusting("localhost"))
                    .get(url, 5, Instant.now().plusSeconds(3));
            assertEquals(Boolean.TRUE, good.first().tls().trusted());
            assertEquals(Boolean.TRUE, good.first().tls().hostnameMatches());
            assertTrue(good.first().tls().issuer().contains("CN=localhost"));
            var f = new TlsRedirectService(L).assess(L.normalize(url), good, Instant.now());
            assertFalse(codes(f).contains("CERT_MISMATCH"));
            assertTrue(codes(f).contains("NEW_CERTIFICATE"), "test certificate was made today: " + f);

            // the same self-signed certificate seen with the normal trust store: not trusted
            var bad = new SafeHttp(StubServers.loopbackGuard(), 1000, 1 << 20, null).get(url, 5, Instant.now().plusSeconds(3));
            assertEquals(Boolean.FALSE, bad.first().tls().trusted());
            assertTrue(codes(new TlsRedirectService(L).assess(L.normalize(url), bad, Instant.now())).contains("CERT_UNTRUSTED"));
        } finally {
            s.stop(0);
        }
    }

    @Test
    void four_hop_redirect_chain_is_followed_and_reported() throws Exception {
        var s = StubServers.http();
        try {
            int p = s.getAddress().getPort();
            StubServers.redirect(s, "/r1", "/r2");
            StubServers.redirect(s, "/r2", "http://127.0.0.1:" + p + "/r3");  // different host
            StubServers.redirect(s, "/r3", "/r4");
            StubServers.redirect(s, "/r4", "http://fleepkart.test:" + p + "/login");
            StubServers.reply(s, "/login", 200, "text/html", "<html><title>Login</title></html>");
            var http = new SafeHttp(StubServers.loopbackGuard("fleepkart.test"), 1000, 1 << 20, null);
            String url = "http://localhost:" + p + "/r1";
            var c = http.get(url, TlsRedirectService.MAX_REDIRECTS, Instant.now().plusSeconds(3));
            assertEquals(5, c.hops().size(), "4 redirects + the final page");
            assertFalse(c.tooManyRedirects());
            assertEquals("http://fleepkart.test:" + p + "/login", TlsRedirectService.finalUrl(c));
            var f = new TlsRedirectService(L).assess(L.normalize(url), c, Instant.now());
            assertTrue(codes(f).contains("REDIRECTS_ELSEWHERE"), f.toString());
            assertTrue(codes(f).contains("LOOKALIKE_BRAND"), "the end of the chain is a fake Flipkart: " + f);
        } finally {
            s.stop(0);
        }
    }

    @Test
    void more_than_five_redirects_is_reported() throws Exception {
        var s = StubServers.http();
        try {
            for (int i = 1; i <= 7; i++) StubServers.redirect(s, "/h" + i, "/h" + (i + 1));
            var http = new SafeHttp(StubServers.loopbackGuard(), 1000, 1 << 20, null);
            String url = "http://localhost:" + s.getAddress().getPort() + "/h1";
            var c = http.get(url, TlsRedirectService.MAX_REDIRECTS, Instant.now().plusSeconds(3));
            assertTrue(c.tooManyRedirects());
            assertTrue(codes(new TlsRedirectService(L).assess(L.normalize(url), c, Instant.now())).contains("TOO_MANY_REDIRECTS"));
        } finally {
            s.stop(0);
        }
    }

    @Test
    void body_is_capped_and_deadline_respected() throws Exception {
        var s = StubServers.http();
        try {
            StubServers.reply(s, "/big", 200, "text/html", "x".repeat(300_000));
            StubServers.slow(s, "/slow", 5_000, "late");
            int p = s.getAddress().getPort();
            var http = new SafeHttp(StubServers.loopbackGuard(), 1000, 100_000, null);
            var big = http.get("http://localhost:" + p + "/big", 0, Instant.now().plusSeconds(3)).last();
            assertEquals(100_000, big.body().length);
            assertTrue(big.truncated());
            long t0 = System.nanoTime();
            var slow = http.get("http://localhost:" + p + "/slow", 0, Instant.now().plusMillis(800));
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertTrue(ms < 1500, "gave up after " + ms + " ms");
            assertTrue(slow.hops().isEmpty() || slow.last().truncated(), slow.toString());
        } finally {
            s.stop(0);
        }
    }
}
