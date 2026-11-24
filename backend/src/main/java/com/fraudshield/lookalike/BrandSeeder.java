package com.fraudshield.lookalike;

import com.fraudshield.domain.BrandEntry;
import com.fraudshield.repo.Repositories.BrandRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 10.1: fills the brands table from seed/brands.csv (adds missing brands only, never overwrites edits),
 * then makes the look-alike engine use the table.
 */
@Component
public class BrandSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BrandSeeder.class);
    private final BrandRepository repo;
    private final LookalikeService service;

    public BrandSeeder(BrandRepository repo, LookalikeService service) { this.repo = repo; this.service = service; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int added = 0;
        for (var b : BrandRegistry.seedFromClasspath()) {
            if (repo.findByName(b.name()).isPresent()) continue;
            repo.save(new BrandEntry(b.name(), b.category(), String.join("|", b.officialHosts()), String.join("|", b.keywords()), "seed-2026-11"));
            added++;
        }
        if (added > 0) log.info("Brands: {} seeded", added);
        service.reload(repo.findAll());
        log.info("Look-alike engine: {} brands", service.registry().size());
    }
}
