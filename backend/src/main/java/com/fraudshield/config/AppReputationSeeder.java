package com.fraudshield.config;

import com.fraudshield.domain.AppReputation;
import com.fraudshield.repo.Repositories.AppReputationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 6.2: loads seed/known-good-apps.csv at start-up. Adds missing rows only; never overwrites admin edits. */
@Component
public class AppReputationSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AppReputationSeeder.class);
    private final AppReputationRepository repo;
    public AppReputationSeeder(AppReputationRepository repo) { this.repo = repo; }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int added = 0;
        try (var in = new BufferedReader(new InputStreamReader(
                new ClassPathResource("seed/known-good-apps.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("package_name")) continue;
                String[] p = line.split(",", 2);
                String pkg = p[0].trim();
                if (pkg.isEmpty() || repo.findByPackageName(pkg).isPresent()) continue;
                repo.save(new AppReputation(pkg, p.length > 1 ? p[1].trim() : null, "GOOD", "seed-2026-11"));
                added++;
            }
        }
        if (added > 0) log.info("App reputation: {} known-good apps seeded", added);
    }
}
