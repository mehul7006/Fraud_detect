package com.fraudshield.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** 6.2 gate data: the seeded known-good list (seed/known-good-apps.csv). */
class KnownGoodSeedTest {
    private static final Pattern PACKAGE = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+");

    private static List<String[]> rows() throws Exception {
        String csv;
        try (var in = new ClassPathResource("seed/known-good-apps.csv").getInputStream()) {
            csv = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String[]> out = new ArrayList<>();
        for (String line : csv.split("\\r?\\n")) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("package_name")) continue;
            out.add(line.split(",", -1));
        }
        return out;
    }

    @Test
    void atLeast500DistinctValidPackages() throws Exception {
        var rows = rows();
        assertTrue(rows.size() >= 500, "only " + rows.size() + " known-good apps");
        Set<String> seen = new HashSet<>();
        for (String[] r : rows) {
            assertEquals(2, r.length, "package_name,label only (no commas in label): " + String.join(",", r));
            assertTrue(PACKAGE.matcher(r[0]).matches(), "bad package " + r[0]);
            assertFalse(r[1].isBlank(), "label missing for " + r[0]);
            assertTrue(seen.add(r[0]), "duplicate " + r[0]);
        }
    }

    @Test
    void remoteAccessAppsAreNeverOnTheKnownGoodList() throws Exception {
        for (String[] r : rows()) {
            assertFalse(CloneRiskService.REMOTE_APPS.contains(r[0]), r[0] + " is a remote-access app (2.x warns about it)");
        }
    }

    @Test
    void lookAlikePackageFoundDuringVerificationIsNotListed() throws Exception {
        // com.blinkit.app is published on Google Play by an unrelated developer; the real Blinkit is com.grofers.customerapp
        var pkgs = rows().stream().map(r -> r[0]).toList();
        assertFalse(pkgs.contains("com.blinkit.app"));
        assertTrue(pkgs.contains("com.grofers.customerapp"));
    }
}
