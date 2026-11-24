package com.fraudshield.lookalike;

import java.nio.file.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Manual tool (skipped unless -Dfp.file is given): lists Tranco domains the matcher would flag. */
class LookalikeFalsePositiveScan {
    @Test
    void scan() throws Exception {
        String f = System.getProperty("fp.file");
        Assumptions.assumeTrue(f != null);
        var n = DomainNormalizer.fromClasspath();
        var m = new LookalikeMatcher(new BrandRegistry(BrandRegistry.seedFromClasspath(), n), PopularDomains.fromClasspath());
        int from = Integer.getInteger("fp.from", 10001), to = Integer.getInteger("fp.to", 200000), flagged = 0, total = 0;
        StringBuilder out = new StringBuilder();
        for (String line : Files.readAllLines(Path.of(f))) {
            String[] c = line.split(",");
            int r = Integer.parseInt(c[0]);
            if (r < from) continue;
            if (r > to) break;
            total++;
            var x = m.check(n.normalize(c[1].strip()), null);
            if (x.isPresent() && x.get().score() >= 70) {
                flagged++;
                out.append(r).append(',').append(c[1].strip()).append(',').append(x.get().brand().name()).append(',')
                   .append(x.get().kind()).append(',').append(x.get().score()).append(',').append(x.get().token()).append('\n');
            }
        }
        Files.writeString(Path.of(System.getProperty("fp.out", "/out/fp.csv")), out);
        System.out.println("FPSCAN total=" + total + " flagged=" + flagged);
    }
}
