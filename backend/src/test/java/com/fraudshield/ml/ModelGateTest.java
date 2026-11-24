package com.fraudshield.ml;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * 11.9 CI gate: every build scores the 11,480 held-out test links (domains never seen in training) with the shipped
 * model, prints the metrics (also to target/model-metrics.md) and FAILS when the model got worse.
 *
 * <p>Gate = the levels url-v1 reaches today minus a small margin, so a worse model or a feature change that breaks
 * the model cannot be merged. The roadmap target (recall >= 90 % at <= 1 % false positives) is NOT reached yet by
 * the address-only model (see ml/reports/url-v1.md); when a better model ships, raise these numbers.
 */
class ModelGateTest {
    static final double MIN_AUC = 0.98, MIN_RECALL = 0.84, MAX_FPR = 0.02;

    record Metrics(double auc, double recall, double fpr, double precision, int n) {}

    static Metrics measure(double[] score, int[] label, double threshold) {
        int tp = 0, fp = 0, fn = 0, tn = 0, n = score.length;
        for (int i = 0; i < n; i++) {
            boolean flag = score[i] >= threshold;
            if (label[i] == 1) { if (flag) tp++; else fn++; } else { if (flag) fp++; else tn++; }
        }
        return new Metrics(auc(score, label), tp / (double) Math.max(1, tp + fn), fp / (double) Math.max(1, fp + tn),
                tp / (double) Math.max(1, tp + fp), n);
    }

    /** Mann-Whitney AUC with average ranks for ties. */
    static double auc(double[] s, int[] y) {
        Integer[] idx = new Integer[s.length];
        for (int i = 0; i < s.length; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> s[i]));
        double[] rank = new double[s.length];
        for (int i = 0; i < s.length; ) {
            int j = i;
            while (j + 1 < s.length && s[idx[j + 1]] == s[idx[i]]) j++;
            for (int k = i; k <= j; k++) rank[idx[k]] = (i + j) / 2.0 + 1;
            i = j + 1;
        }
        double pos = 0, sumPos = 0;
        for (int i = 0; i < s.length; i++) if (y[i] == 1) { pos++; sumPos += rank[i]; }
        double neg = s.length - pos;
        return (sumPos - pos * (pos + 1) / 2) / (pos * neg);
    }

    static List<String> gate(Metrics m) {
        List<String> f = new ArrayList<>();
        if (m.auc() < MIN_AUC) f.add(String.format("AUC %.4f < %.2f", m.auc(), MIN_AUC));
        if (m.recall() < MIN_RECALL) f.add(String.format("recall %.4f < %.2f", m.recall(), MIN_RECALL));
        if (m.fpr() > MAX_FPR) f.add(String.format("false-positive rate %.4f > %.2f", m.fpr(), MAX_FPR));
        return f;
    }

    static List<String[]> testSet() throws Exception {
        String tsv;
        try (var in = ModelGateTest.class.getResourceAsStream("/ml/test-set.tsv")) { tsv = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        List<String[]> rows = new ArrayList<>();
        for (String l : tsv.split("\n")) if (!l.isBlank() && !l.startsWith("#")) rows.add(l.split("\t", 2));
        return rows;
    }

    @Test
    void shipped_model_keeps_its_quality_on_held_out_domains() throws Exception {
        var ml = MlScoreService.defaults();
        assertTrue(ml.enabled(), "the shipped model must load");
        var rows = testSet();
        double[] s = new double[rows.size()];
        int[] y = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) { y[i] = Integer.parseInt(rows.get(i)[0]); s[i] = ml.probability(rows.get(i)[1]); }
        Metrics m = measure(s, y, ml.threshold());
        String report = String.format(Locale.ROOT, "# model %s on %d held-out links%n%nAUC %.4f | recall %.4f | false positives %.4f | precision %.4f%n"
                + "Gate: AUC >= %.2f, recall >= %.2f, false positives <= %.2f%n", ml.version(), m.n(), m.auc(), m.recall(), m.fpr(), m.precision(),
                MIN_AUC, MIN_RECALL, MAX_FPR);
        System.out.println(report);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "model-metrics.md"), report);
        assertEquals(List.of(), gate(m), report);
    }

    /** The gate really fails a worse model: same data, scores degraded (noise) -> gate failures. */
    @Test
    void a_degraded_model_fails_the_gate() throws Exception {
        var ml = MlScoreService.defaults();
        var rows = testSet().subList(0, 3000);
        var rnd = new Random(1);
        double[] s = new double[rows.size()];
        int[] y = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            y[i] = Integer.parseInt(rows.get(i)[0]);
            s[i] = 0.4 * ml.probability(rows.get(i)[1]) + 0.6 * rnd.nextDouble(); // a much weaker model
        }
        assertFalse(gate(measure(s, y, ml.threshold())).isEmpty(), "gate must reject a degraded model");
    }
}
