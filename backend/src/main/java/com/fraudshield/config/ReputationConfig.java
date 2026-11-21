package com.fraudshield.config;

import com.fraudshield.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class ReputationConfig {
    /** Safe Browsing is on only when SAFE_BROWSING_KEY is set; otherwise a no-op provider keeps scans working. */
    @Bean
    ReputationProvider reputationProvider(
            @Value("${fraudshield.safebrowsing.api-key:}") String key,
            @Value("${fraudshield.safebrowsing.endpoint:https://safebrowsing.googleapis.com/v4/threatMatches:find}") String endpoint) {
        if (key == null || key.isBlank()) return new NoopReputationProvider();
        return new CachingReputationProvider(new SafeBrowsingProvider(endpoint, key), 10_000, Duration.ofHours(24));
    }
}
