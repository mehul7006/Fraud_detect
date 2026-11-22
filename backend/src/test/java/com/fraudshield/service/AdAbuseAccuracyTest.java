package com.fraudshield.service;

import com.fraudshield.config.AdAbuseProperties;
import com.fraudshield.domain.Enums.Verdict;
import com.fraudshield.dto.Dtos.AppInfo;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Roadmap 6.1: precision/recall of the ad-abuse score on the labelled set (flagged = MALICIOUS).
 *
 * Before/after note (on the 30 synthetic profiles in adabuse-labelled.csv):
 *  - before (accessibility 25, sms 20, self-launch high 35): precision 100%, recall 10/15.
 *    Missed: remote access trojan (65), flashlight adware (60), SMS stealer, notification spammer, OTP stealer.
 *  - after  (accessibility 30, sms 25, self-launch high 45): precision 100%, recall 12/15.
 *    Still missed on purpose: SMS stealer (40), OTP stealer (55), pure notification spammer (50). Raising those
 *    weights further would flag the default SMS app / busy chat apps, so they stay SUSPICIOUS (shown, not alarmed).
 */
class AdAbuseAccuracyTest {
    record Row(boolean abusive, String name, AppInfo app) {}
    record Metrics(int tp, int fp, int fn, int tn, List<String> falsePositives, List<String> missed) {
        double precision() { return tp + fp == 0 ? 1 : (double) tp / (tp + fp); }
        double recall() { return tp + fn == 0 ? 1 : (double) tp / (tp + fn); }
    }

    static List<Row> load() throws Exception { return load("/adabuse-labelled.csv"); }

    /** Same CSV format as GET /api/v1/admin/apps/labels.csv; null when the resource does not exist. */
    static List<Row> load(String resource) throws Exception {
        var stream = AdAbuseAccuracyTest.class.getResourceAsStream(resource);
        if (stream == null) return null;
        return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }

    static List<Row> parse(String csv) throws Exception {
        List<Row> rows = new ArrayList<>();
        try (var in = new BufferedReader(new java.io.StringReader(csv))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("label,")) continue;
                String[] p = line.split(",");
                var app = new AppInfo("test." + rows.size(), p[1], Integer.parseInt(p[2]), Boolean.parseBoolean(p[3]),
                        Boolean.parseBoolean(p[4]), Boolean.parseBoolean(p[5]), Boolean.parseBoolean(p[6]),
                        Boolean.parseBoolean(p[7]), Boolean.parseBoolean(p[8]), Integer.parseInt(p[9]));
                rows.add(new Row("ABUSIVE".equals(p[0]), p[1], app));
            }
        }
        return rows;
    }

    static Metrics measure(AdAbuseService svc, List<Row> rows) {
        int tp = 0, fp = 0, fn = 0, tn = 0;
        List<String> fps = new ArrayList<>(), missed = new ArrayList<>();
        for (Row r : rows) {
            var res = svc.score(r.app());
            boolean flagged = res.verdict() == Verdict.MALICIOUS;
            if (flagged && r.abusive()) tp++;
            else if (flagged) { fp++; fps.add(r.name() + " (" + res.adAbuseScore() + ")"); }
            else if (r.abusive()) { fn++; missed.add(r.name() + " (" + res.adAbuseScore() + ")"); }
            else tn++;
        }
        return new Metrics(tp, fp, fn, tn, fps, missed);
    }

    static AdAbuseProperties before() {
        var p = new AdAbuseProperties();
        p.setAccessibilityWeight(25); p.setSmsWeight(20); p.setSelfLaunchHighWeight(35);
        return p;
    }

    private static String line(String tag, Metrics m) {
        return String.format("ADABUSE %s: precision %.0f%% recall %d/%d  FP=%s  missed=%s", tag,
                m.precision() * 100, m.tp(), m.tp() + m.fn(), m.falsePositives(), m.missed());
    }

    @Test
    void precisionIsAtLeast90PercentOn30LabelledApps() throws Exception {
        var rows = load();
        assertEquals(30, rows.size());
        Metrics old = measure(new AdAbuseService(before()), rows);
        Metrics now = measure(new AdAbuseService(), rows); // defaults = tuned weights
        System.out.println(line("before", old));
        System.out.println(line("after ", now));
        assertTrue(now.precision() >= 0.90, line("after", now));
        assertTrue(now.recall() >= old.recall(), "tuning must not lose recall");
    }

    /**
     * REAL apps measured in the lab (adabuse-lab.csv: 7 real adware from MalwareBazaar + 29 real F-Droid apps, see the
     * file header). First real measurement 2026-11-22: precision 50%, recall 1/7 - the 6.1 gate (90%) is NOT met.
     * This test prints the numbers and only blocks regressions below that baseline; raise the floor as signals improve.
     */
    static final double LAB_PRECISION_FLOOR = 0.50;
    static final int LAB_TP_FLOOR = 1;

    @Test
    void realLabSet_reportsHonestNumbers_andNeverGetsWorse() throws Exception {
        var rows = load("/adabuse-lab.csv");
        org.junit.jupiter.api.Assumptions.assumeTrue(rows != null && !rows.isEmpty(), "no lab set");
        Metrics m = measure(new AdAbuseService(), rows);
        System.out.println(line("lab   ", m) + "   (6.1 gate is 90%)");
        assertTrue(m.tp() >= LAB_TP_FLOOR && m.precision() >= LAB_PRECISION_FLOOR, "regression: " + line("lab", m));
    }

    /** Real opt-in beta labels (export of /admin/apps/labels.csv saved as adabuse-real.csv). Gate: >=30 rows, >=90%. */
    static final int REAL_NEEDED = 30;

    @Test
    void precisionIsAtLeast90PercentOnRealBetaLabels() throws Exception {
        var rows = load("/adabuse-real.csv");
        int n = rows == null ? 0 : rows.size();
        org.junit.jupiter.api.Assumptions.assumeTrue(n >= REAL_NEEDED,
                "6.1 gate waits for real data: " + n + " of " + REAL_NEEDED + " real labelled apps (adabuse-real.csv)");
        Metrics m = measure(new AdAbuseService(), rows);
        System.out.println(line("real  ", m));
        // precision() is 100% when nothing is flagged; that must not count as passing
        assertTrue(m.tp() >= 1, "no real abusive app was flagged at all: " + line("real", m));
        assertTrue(m.precision() >= 0.90, line("real", m));
    }
}
