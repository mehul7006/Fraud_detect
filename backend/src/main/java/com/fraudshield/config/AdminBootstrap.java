package com.fraudshield.config;

import com.fraudshield.repo.Repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Promotes the user named in ADMIN_EMAIL to ADMIN at start-up. There is deliberately no public endpoint for this. */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final UserRepository users;
    private final String adminEmail;

    public AdminBootstrap(UserRepository users, @Value("${fraudshield.admin-email:}") String adminEmail) {
        this.users = users; this.adminEmail = adminEmail;
    }

    @Override
    public void run(ApplicationArguments args) { promote(); }

    public void promote() {
        if (adminEmail == null || adminEmail.isBlank()) return;
        users.findByEmail(adminEmail.trim().toLowerCase()).ifPresentOrElse(u -> {
            if (!"ADMIN".equals(u.getRole())) { u.setRole("ADMIN"); users.save(u); log.info("Admin role granted at start-up"); }
        }, () -> log.info("ADMIN_EMAIL is set but that user has not registered yet"));
    }
}
