package com.fraudshield.ml;

import com.fraudshield.lookalike.LookalikeService;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * 11.2 tool (skipped unless -Dml.in=dataset.tsv -Dml.out=features.csv): writes the model features of every URL,
 * computed by the production {@link FeatureExtractor}. ml/train.py reads this file.
 */
class FeatureDumpTool {
    @Test
    void dump() throws Exception {
        String in = System.getProperty("ml.in"), out = System.getProperty("ml.out");
        Assumptions.assumeTrue(in != null && out != null);
        var fx = new FeatureExtractor(LookalikeService.defaults());
        try (BufferedWriter w = Files.newBufferedWriter(Path.of(out), StandardCharsets.UTF_8)) {
            w.write("label,split," + String.join(",", FeatureExtractor.NAMES) + "\n");
            for (String line : Files.readAllLines(Path.of(in), StandardCharsets.UTF_8)) {
                Row r = Row.parse(line);
                if (r == null) continue;
                double[] f = fx.extract(r.url);
                StringBuilder b = new StringBuilder().append(r.label).append(',').append(r.split);
                for (double v : f) b.append(',').append(fmt(v));
                w.write(b.append('\n').toString());
            }
        }
    }

    static String fmt(double v) { return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.4f", v); }

    /** dataset.tsv: label TAB split TAB url. */
    record Row(String url, String label, String split) {
        static Row parse(String line) {
            String[] p = line.split("\t", 3);
            return p.length < 3 ? null : new Row(p[2], p[0], p[1]);
        }
    }
}
