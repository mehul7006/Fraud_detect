package com.fraudshield.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.repo.Repositories.BlocklistRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 10.12: precision and recall of the link check on src/test/resources/url-corpus.csv (134 phishing-style Indian
 * links, 188 legitimate incl. 80 random less-known real sites). "Flagged" = the user sees a warning (not SAFE).
 * Offline: the address layer only (no page fetch, no Safe Browsing) - the network layers can only add to this.
 * Writes target/url-accuracy-report.md with every false positive and every miss.
 */
class UrlAccuracyTest {

    @Test
    void precision_at_least_95_and_recall_at_least_90_percent() throws Exception {
        var repo = Mockito.mock(BlocklistRepository.class);
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        var scanner = new UrlScanService(repo);

        String csv;
        try (var in = getClass().getResourceAsStream("/url-corpus.csv")) { csv = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        int tp = 0, fp = 0, fn = 0, tn = 0, malicious = 0;
        List<String> fps = new ArrayList<>(), fns = new ArrayList<>();
        for (String line : csv.split("\\r?\\n")) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("url,")) continue;
            String[] c = line.split(",");
            String url = c[0], label = c[1], cat = c[2];
            var r = scanner.scan(url);
            boolean flagged = r.verdict() != Verdict.SAFE;
            if (label.equals("PHISH")) {
                if (flagged) tp++; else { fn++; fns.add("| " + url + " | " + cat + " | " + r.score() + " | " + String.join("; ", r.reasons()) + " |"); }
                if (r.verdict() == Verdict.MALICIOUS) malicious++;
            } else {
                if (flagged) { fp++; fps.add("| " + url + " | " + cat + " | " + r.verdict() + " " + r.score() + " | " + String.join("; ", r.reasons()) + " |"); }
                else tn++;
            }
        }
        double precision = tp / (double) Math.max(1, tp + fp), recall = tp / (double) Math.max(1, tp + fn);
        String report = "# 10.12 link-check accuracy (address layer, offline)\n\n"
                + String.format("Phishing-style: %d, flagged %d (MALICIOUS %d), missed %d%n", tp + fn, tp, malicious, fn)
                + String.format("Legitimate: %d, flagged %d (false positives)%n%n", fp + tn, fp)
                + String.format("**Precision %.1f%%, recall %.1f%%** (gate: precision >= 95%%, recall >= 90%%)%n%n", precision * 100, recall * 100)
                + "## False positives (legitimate sites that got a warning)\n\n| URL | kind | verdict | reasons |\n|---|---|---|---|\n"
                + (fps.isEmpty() ? "| none | | | |\n" : String.join("\n", fps) + "\n")
                + "\n## Missed phishing-style links\n\n| URL | kind | score | reasons |\n|---|---|---|---|\n"
                + (fns.isEmpty() ? "| none | | | |\n" : String.join("\n", fns) + "\n");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "url-accuracy-report.md"), report);
        System.out.println(report);
        assertTrue(tp + fn >= 100 && fp + tn >= 100, "corpus too small");
        assertTrue(precision >= 0.95, String.format("precision %.3f%n%s", precision, report));
        assertTrue(recall >= 0.90, String.format("recall %.3f%n%s", recall, report));
    }
}
