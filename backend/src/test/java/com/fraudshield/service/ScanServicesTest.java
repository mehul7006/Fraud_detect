package com.fraudshield.service;

import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.AppInfo;
import com.fraudshield.dto.Dtos.FileScanRequest;
import com.fraudshield.repo.Repositories.BlocklistRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanServicesTest {
    private final BlocklistRepository repo = Mockito.mock(BlocklistRepository.class);

    @Test
    void phishingUrlIsFlagged() {
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        var r = new UrlScanService(repo).scan("http://whatsapp.verify-login-now.xyz/update");
        assertTrue(r.score() >= 70, "score was " + r.score());
        assertEquals(Verdict.MALICIOUS, r.verdict());
    }

    /** 10.9: layered score with reason codes, impersonated brand and the real site. */
    @Test
    void lookalikeLinkNamesBrandAndRealSite() {
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        var r = new UrlScanService(repo).scan("http://sbi-kyc-update.top/verify");
        assertEquals(Verdict.MALICIOUS, r.verdict());
        assertEquals("State Bank of India", r.impersonatedBrand());
        assertEquals("https://www.sbi.co.in", r.officialUrl());
        assertTrue(r.reasonCodes().containsAll(java.util.List.of("LOOKALIKE_BRAND", "NO_HTTPS", "RISKY_TLD", "BAIT_WORDS")), r.reasonCodes().toString());
        assertEquals(r.reasons().size(), r.reasonCodes().size());
        var hidden = new UrlScanService(repo).scan("https://login.flipkart.com.evil.xyz/");
        assertEquals("Flipkart", hidden.impersonatedBrand());
        assertTrue(hidden.reasonCodes().contains("BRAND_IN_SUBDOMAIN"));
        var path = new UrlScanService(repo).scan("https://random-shop.example/flipkart/sale");
        assertEquals(null, path.impersonatedBrand(), "brand only in the path: no 'looks like' claim");
        assertTrue(path.reasonCodes().contains("BRAND_IN_PATH"));
        var ok = new UrlScanService(repo).scan("https://www.flipkart.com/");
        assertEquals(Verdict.SAFE, ok.verdict());
        assertEquals(null, ok.impersonatedBrand());
    }

    /** A blocklisted registrable domain also covers its sub-domains. */
    @Test
    void blocklistCoversSubdomains() {
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(repo.findByKindAndValue("DOMAIN", "bad-site.in")).thenReturn(Optional.of(Mockito.mock(com.fraudshield.domain.Blocklist.class)));
        assertEquals(100, new UrlScanService(repo).scan("https://pay.bad-site.in/x").score());
    }

    @Test
    void normalUrlIsSafe() {
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        assertEquals(Verdict.SAFE, new UrlScanService(repo).scan("https://www.google.com/search?q=hi").verdict());
    }

    @Test
    void doubleExtensionApkIsMalicious() {
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        var req = new FileScanRequest("a".repeat(64), "invoice.pdf.apk", 900_000, "application/vnd.android.package-archive",
                "com.whatsapp", "504b0304");
        assertEquals(Verdict.MALICIOUS, new FileScanService(repo).scan(req).verdict());
    }

    @Test
    void adSpammerScoresHigh() {
        var app = new AppInfo("com.spam.app", "Spam", 30, true, false, false, false, true, false);
        assertTrue(new AdAbuseService().score(app).adAbuseScore() >= 70);
    }
}
