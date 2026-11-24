package com.fraudshield.message;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 14.6: the server's copy of the message model gives the same answers as training (100 held-out messages). */
class MessageModelTest {
    @Test
    void same_probabilities_as_training() throws Exception {
        var m = MessageModel.fromClasspath();
        String tsv;
        try (var in = getClass().getResourceAsStream("/ml/msg-vectors.tsv")) { tsv = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        int n = 0;
        for (String line : tsv.split("\n")) {
            if (line.isBlank()) continue;
            String[] c = line.split("\t", 3);
            var r = m.classify(c[2]);
            assertEquals(Double.parseDouble(c[1]), 1 - r.scamProbability(), 1e-4, c[2]);
            if (!c[0].equals("GENUINE")) assertEquals(c[0], r.category(), c[2]);
            n++;
        }
        assertEquals(100, n);
        assertEquals("zzurl verify 0", MessageModel.normalise("HTTPS://x.top/a  Verify ४५६"));
    }
}
