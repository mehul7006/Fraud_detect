package com.fraudshield.service;

import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.FileScanRequest;
import com.fraudshield.repo.Repositories.BlocklistRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Roadmap 5.1 (VirusTotal by hash) and 5.2 (content flags). */
class FileReputationTest {
    private static final String SHA = "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f";
    private final BlocklistRepository repo = Mockito.mock(BlocklistRepository.class);
    private HttpServer server;
    private volatile int status = 200;
    private volatile String body = "";
    private volatile long delayMs = 0;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastKeyHeader = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            calls.incrementAndGet();
            lastPath.set(ex.getRequestURI().getPath());
            lastKeyHeader.set(ex.getRequestHeaders().getFirst("x-apikey"));
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { }
            byte[] b = body.getBytes();
            try { ex.sendResponseHeaders(status, b.length); ex.getResponseBody().write(b); } catch (java.io.IOException ignored) { }
            ex.close();
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    private VirusTotalClient vt(int perMinute) {
        return new VirusTotalClient("http://127.0.0.1:" + server.getAddress().getPort(), "secret-key", perMinute);
    }

    private static final String VT_BODY = "{\"data\":{\"attributes\":{\"last_analysis_stats\":{\"malicious\":62,\"suspicious\":1,\"undetected\":8,\"harmless\":0}}}}";

    private FileScanRequest req(String name, String... flags) {
        return new FileScanRequest(SHA, name, 5000, null, "com.whatsapp", "25504446", List.of(flags));
    }

    // ---------- 5.1 ----------
    @Test void parsesEngineCounts() {
        var r = VirusTotalClient.parse(VT_BODY);
        assertEquals(62, r.malicious());
        assertEquals(71, r.total());
    }

    @Test void clientSendsOnlyTheHashInThePathAndKeyInHeader() {
        body = VT_BODY;
        var r = vt(4).lookup(SHA);
        assertTrue(r.isPresent());
        assertEquals("/files/" + SHA, lastPath.get());
        assertEquals("secret-key", lastKeyHeader.get());
    }

    @Test void maliciousResultRaisesScoreAndAddsReason() {
        body = VT_BODY;
        var res = new FileScanService(repo, vt(4)).scan(req("a.txt"));
        assertEquals(Verdict.MALICIOUS, res.verdict());
        assertTrue(res.reasons().contains("Flagged by 62 of 71 antivirus engines"), res.reasons().toString());
    }

    @Test void unknownHashKeepsScore() {
        status = 404;
        var res = new FileScanService(repo, vt(4)).scan(req("a.txt"));
        assertEquals(0, res.score());
    }

    @Test void timeoutAndServerErrorDoNotFailTheScan() {
        delayMs = 3000;
        assertEquals(0, new FileScanService(repo, vt(4)).scan(req("a.txt")).score());
        delayMs = 0; status = 500;
        assertEquals(0, new FileScanService(repo, vt(4)).scan(req("a.txt")).score());
    }

    @Test void cacheAndRateLimitProtectTheQuota() {
        body = VT_BODY;
        var c = vt(2);
        c.lookup(SHA); c.lookup(SHA); c.lookup(SHA);
        assertEquals(1, calls.get(), "same hash is served from cache");
        c.lookup("b".repeat(64));
        assertEquals(2, calls.get());
        assertTrue(c.lookup("c".repeat(64)).isEmpty(), "third distinct hash in a minute exceeds the limit of 2");
        assertEquals(2, calls.get());
    }

    @Test void disabledClientLeavesScanUntouched() {
        var res = new FileScanService(repo, sha -> Optional.empty()).scan(req("a.txt"));
        assertEquals(0, res.score());
    }

    // ---------- 5.2 ----------
    private final FileScanService plain = new FileScanService(repo);

    @Test void cleanPdfStaysBelow30() {
        var r = plain.scan(new FileScanRequest("d".repeat(64), "bill.pdf", 90_000, null, "downloads", "25504446", null));
        assertTrue(r.score() < 30, "score " + r.score());
    }

    @Test void pdfWithJavaScriptIsSuspicious() {
        var r = plain.scan(new FileScanRequest("d".repeat(64), "bill.pdf", 90_000, null, "downloads", "25504446", List.of("PDF_JS")));
        assertEquals(Verdict.SUSPICIOUS, r.verdict());
        assertTrue(r.reasons().contains("PDF contains JavaScript"));
    }

    @Test void pdfWithLaunchIsMalicious() {
        var r = plain.scan(new FileScanRequest("d".repeat(64), "bill.pdf", 90_000, null, "downloads", "25504446", List.of("PDF_LAUNCH")));
        assertTrue(r.score() >= 70, "score " + r.score());
        assertEquals("DELETE", r.action());
    }

    @Test void docxWithMacroFlagIsSuspicious() {
        var r = plain.scan(new FileScanRequest("d".repeat(64), "salary.docx", 40_000, null, "downloads", "504b0304", List.of("OFFICE_MACRO")));
        assertEquals(Verdict.SUSPICIOUS, r.verdict());
    }

    @Test void unknownFlagsAreIgnoredAndRequestWithoutFlagsStillWorks() {
        var r = plain.scan(new FileScanRequest("d".repeat(64), "bill.pdf", 90_000, null, "downloads", "25504446", List.of("NONSENSE")));
        assertTrue(r.score() < 30);
        var old = plain.scan(new FileScanRequest("d".repeat(64), "bill.pdf", 90_000, null, "downloads", "25504446"));
        assertEquals(r.score(), old.score());
    }
}
