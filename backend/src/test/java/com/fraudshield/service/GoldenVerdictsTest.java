package com.fraudshield.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * 11.5: 30 golden links keep their verdict (rules + model, offline). A change here must be deliberate: update
 * src/test/resources/golden-verdicts.tsv and say why in the commit. Official / gov.in sites are SAFE even with bait
 * words; the model can raise, never lower.
 */
class GoldenVerdictsTest {
    @Test
    void verdicts_are_stable() throws Exception {
        var repo = Mockito.mock(BlocklistRepository.class);
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        var scan = new UrlScanService(repo);
        String tsv;
        try (var in = getClass().getResourceAsStream("/golden-verdicts.tsv")) { tsv = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        List<String> actual = new ArrayList<>(), diffs = new ArrayList<>();
        int n = 0;
        for (String line : tsv.split("\n")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] c = line.split("\t");
            var r = scan.scan(c[0]);
            actual.add(c[0] + "\t" + r.verdict());
            if (!r.verdict().name().equals(c[1].strip())) diffs.add(c[0] + ": expected " + c[1] + ", got " + r.verdict() + " " + r.score() + " " + r.reasonCodes());
            n++;
        }
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target", "golden-verdicts.actual.tsv"), actual);
        assertEquals(30, n, "30 golden cases");
        assertEquals(List.of(), diffs);
    }
}
