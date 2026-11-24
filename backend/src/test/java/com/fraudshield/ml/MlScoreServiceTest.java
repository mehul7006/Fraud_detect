package com.fraudshield.ml;

import static org.junit.jupiter.api.Assertions.*;

import com.fraudshield.lookalike.LookalikeService;
import com.fraudshield.repo.Repositories.BlocklistRepository;
import com.fraudshield.service.NoopReputationProvider;
import com.fraudshield.service.UrlScanService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 11.2 feature parity, 11.4 model serving + fallback. */
class MlScoreServiceTest {
    static final LookalikeService L = LookalikeService.defaults();

    /** 100 test URLs: the Java features and the Java ONNX score equal what training (Python) saw and produced. */
    @Test
    void java_features_and_score_match_training() throws Exception {
        var fx = new FeatureExtractor(L);
        var ml = MlScoreService.defaults();
        assertTrue(ml.enabled(), "shipped model loads");
        String tsv;
        try (var in = getClass().getResourceAsStream("/ml/feature-vectors.tsv")) { tsv = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        String[] lines = tsv.split("\n");
        String[] head = lines[0].split("\t");
        assertEquals(FeatureExtractor.NAMES, java.util.List.of(head).subList(2, head.length), "same feature names and order");
        int n = 0;
        for (int li = 1; li < lines.length; li++) {
            if (lines[li].isBlank()) continue;
            String[] c = lines[li].split("\t", -1);
            double[] f = fx.extract(c[0]);
            for (int i = 0; i < f.length; i++)
                assertEquals(Double.parseDouble(c[i + 2]), f[i], 1e-3, c[0] + " feature " + FeatureExtractor.NAMES.get(i));
            assertEquals(Double.parseDouble(c[1]), ml.probability(c[0]), 1e-4, "score of " + c[0]);
            n++;
        }
        assertEquals(100, n);
    }

    @Test
    void missing_or_mismatched_model_means_rules_only() throws Exception {
        var none = new MlScoreService(L, "classpath:models/does-not-exist");
        assertFalse(none.enabled());
        assertNull(none.probability("https://example.com"));

        Path dir = Files.createTempDirectory("model");
        try (var in = getClass().getResourceAsStream("/models/url-v1.onnx")) { Files.write(dir.resolve("m.onnx"), in.readAllBytes()); }
        Files.writeString(dir.resolve("m.json"), "{\"features\":[\"url_len\"],\"threshold\":0.5}");
        assertFalse(new MlScoreService(L, dir.resolve("m").toString()).enabled(), "trained on other features -> off");

        var repo = Mockito.mock(BlocklistRepository.class);
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        var rulesOnly = new UrlScanService(repo, new NoopReputationProvider(), L, null, Duration.ofSeconds(3), none);
        var r = rulesOnly.scan("http://fleepkart.com/login");
        assertNull(r.mlScore());
        assertEquals(com.fraudshield.domain.Enums.Verdict.MALICIOUS, r.verdict(), "rules still work");
    }

    @Test
    void model_adds_its_score_only_for_unknown_sites() {
        var repo = Mockito.mock(BlocklistRepository.class);
        Mockito.when(repo.findByKindAndValue(Mockito.any(), Mockito.any())).thenReturn(Optional.empty());
        var scan = new UrlScanService(repo);
        assertNotNull(scan.scan("https://secure-login-verify.example-bank-update.top/wp-admin/sign-in.php?acc=1").mlScore());
        assertNull(scan.scan("https://www.sbi.co.in/web/personal-banking").mlScore(), "official site: no model");
        assertNull(scan.scan("https://en.wikipedia.org/wiki/Phishing").mlScore(), "popular site: no model");
        assertNull(scan.scan("https://uidai.gov.in/login-verify").mlScore(), "gov.in: no model");
    }
}
