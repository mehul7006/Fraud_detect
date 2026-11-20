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
