package com.fraudshield.config;

import com.fraudshield.service.AppReputationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

/**
 * 6.2: loads seed/known-good-apps.csv at start-up (columns package_name,label[,verdict[,cert_sha256]]).
 * Adds missing rows only; never overwrites admin edits. Bigger lists go through POST /admin/apps/import.
 */
@Component
public class AppReputationSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AppReputationSeeder.class);
    private final AppReputationService service;
    public AppReputationSeeder(AppReputationService service) { this.service = service; }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String csv;
        try (var in = new ClassPathResource("seed/known-good-apps.csv").getInputStream()) {
            csv = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var r = service.importCsv(csv, "seed-2026-11", false);
        if (r.added() > 0) log.info("App reputation: {} apps seeded", r.added());
        if (!r.errors().isEmpty()) log.warn("App reputation seed: {} bad rows, first: {}", r.errors().size(), r.errors().get(0));
    }
}
