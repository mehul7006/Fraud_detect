package com.fraudshield.service;

import com.fraudshield.repo.Repositories.BlocklistRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Roadmap 4.1 (Safe Browsing provider behind an interface) and 4.2 (cache). */
class ReputationTest {
    private HttpServer server;
    private volatile int status = 200;
    private volatile String body = "{}";
    private volatile long delayMs = 0;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            calls.incrementAndGet();
            ex.getRequestBody().readAllBytes();
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { }
            byte[] b = body.getBytes();
            try {
                ex.sendResponseHeaders(status, b.length);
                ex.getResponseBody().write(b);
            } catch (java.io.IOException ignored) { /* client timed out */ }
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    private SafeBrowsingProvider provider() {
        return new SafeBrowsingProvider("http://127.0.0.1:" + server.getAddress().getPort() + "/find", "k");
    }

    @Test void matchGivesScore60() {
        body = "{\"matches\":[{\"threatType\":\"MALWARE\"}]}";
        assertEquals(60, provider().score("http://bad.test/x"));
    }

    @Test void emptyAnswerGivesZero() { assertEquals(0, provider().score("http://ok.test/")); }

    @Test void serverErrorGivesNullNotException() { status = 500; assertNull(provider().score("http://ok.test/")); }

    @Test void timeoutGivesNull() { delayMs = 3000; assertNull(provider().score("http://ok.test/")); }

    @Test void failuresAreNotCachedButSuccessesAre() {
        var cached = new CachingReputationProvider(provider(), 100, Duration.ofHours(1));
        status = 500;
        assertNull(cached.score("http://a.test/p?x=1"));
        assertNull(cached.score("http://a.test/p?x=1"));
        assertEquals(2, calls.get(), "failure must be retried");
        status = 200;
        assertEquals(0, cached.score("http://a.test/p?x=1"));
        assertEquals(0, cached.score("http://a.test/p?x=2")); // query ignored in the key
        assertEquals(3, calls.get(), "success served from cache");
    }

    @Test void secondScanIsFast() {
        body = "{\"matches\":[{}]}";
        var cached = new CachingReputationProvider(provider(), 100, Duration.ofHours(1));
        cached.score("http://b.test/");
        long t0 = System.nanoTime();
        cached.score("http://b.test/");
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("cached lookup took " + ms + " ms");
        assertTrue(ms < 20, "took " + ms + " ms");
    }

    @Test void scanAddsReasonWhenListedAndSurvivesProviderFailure() {
        BlocklistRepository repo = Mockito.mock(BlocklistRepository.class);
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        body = "{\"matches\":[{}]}";
        var listed = new UrlScanService(repo, provider()).scan("https://some-site.com/a");
        assertTrue(listed.score() >= 60);
        assertTrue(listed.reasons().contains("Listed by Google Safe Browsing"));
        status = 500;
        var failed = new UrlScanService(repo, provider()).scan("https://some-site.com/a");
        assertEquals(0, failed.score());
    }
}
