package com.fraudshield.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.AppInfo;
import com.fraudshield.explain.ExplainService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * 13.14: precision / recall of the combined app check (ad-abuse 6.x + privacy / fraud 13.3) on the 50 labelled apps
 * in src/test/resources/app-privacy-labelled.tsv (constructed, see the file). "Flagged" = the user sees a warning.
 * Writes target/app-accuracy-report.md with every false positive and every miss.
 */
class AppAccuracyTest {
    @Test
    void precision_at_least_90_and_recall_at_least_85_percent() throws Exception {
        var scan = new AppScanService(new AdAbuseService(), new AppPrivacyService(), new ExplainService(Set.of(), null));
        String tsv;
        try (var in = getClass().getResourceAsStream("/app-privacy-labelled.tsv")) { tsv = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        int tp = 0, fp = 0, fn = 0, tn = 0, n = 0;
        Map<String, int[]> byKind = new TreeMap<>();
        List<String> fps = new ArrayList<>(), fns = new ArrayList<>();
        for (String line : tsv.split("\n")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] c = Arrays.copyOf(line.split("\t", -1), 9); // trailing empty columns may be missing
            List<String> perms = list(c[6]);
            AppInfo a = new AppInfo(c[2], c[3], 0, perms.contains("SYSTEM_ALERT_WINDOW"),
                    perms.stream().anyMatch(p -> p.contains("SMS")), perms.contains("BIND_ACCESSIBILITY_SERVICE"),
                    perms.contains("BIND_NOTIFICATION_LISTENER_SERVICE"), false, false, 0, null,
                    c[4].equals("PLAY") ? "com.android.vending" : null, perms, perms, Integer.valueOf(c[5]), list(c[7]), list(c[8]), null);
            var r = scan.scan(a);
            boolean flagged = r.verdict() != Verdict.SAFE, risky = c[0].equals("RISKY");
            n++;
            byKind.computeIfAbsent(c[1], k -> new int[2]);
            byKind.get(c[1])[flagged == risky ? 0 : 1]++;
            String detail = "| " + c[3] + " (" + c[2] + ") | " + c[1] + " | " + r.verdict() + " " + r.privacyScore() + " | " + r.reasonCodes() + " |";
            if (risky && flagged) tp++; else if (risky) { fn++; fns.add(detail); }
            else if (flagged) { fp++; fps.add(detail); } else tn++;
        }
        double precision = tp / (double) Math.max(1, tp + fp), recall = tp / (double) Math.max(1, tp + fn);
        StringBuilder rep = new StringBuilder("# 13.14 app check accuracy (50 constructed labelled apps)\n\n")
                .append(String.format(Locale.ROOT, "Risky %d: caught %d, missed %d. Benign %d: warned %d.%n%n", tp + fn, tp, fn, fp + tn, fp))
                .append(String.format(Locale.ROOT, "**Precision %.1f %%, recall %.1f %%** (gate: precision >= 90 %%, recall >= 85 %%)%n%n", precision * 100, recall * 100))
                .append("| kind | right | wrong |\n|---|---|---|\n");
        byKind.forEach((k, v) -> rep.append("| ").append(k).append(" | ").append(v[0]).append(" | ").append(v[1]).append(" |\n"));
        rep.append("\n## False positives\n\n| app | kind | verdict score | codes |\n|---|---|---|---|\n")
           .append(fps.isEmpty() ? "| none | | | |\n" : String.join("\n", fps) + "\n")
           .append("\n## Missed\n\n| app | kind | verdict score | codes |\n|---|---|---|---|\n")
           .append(fns.isEmpty() ? "| none | | | |\n" : String.join("\n", fns) + "\n");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "app-accuracy-report.md"), rep.toString());
        System.out.println(rep);
        assertTrue(n == 50, "50 labelled apps, got " + n);
        assertTrue(precision >= 0.90, rep.toString());
        assertTrue(recall >= 0.85, rep.toString());
    }

    private static List<String> list(String s) { return s == null || s.isBlank() ? List.of() : List.of(s.split(";")); }
}
