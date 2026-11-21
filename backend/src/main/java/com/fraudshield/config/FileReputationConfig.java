package com.fraudshield.config;

import com.fraudshield.service.FileReputationClient;
import com.fraudshield.service.VirusTotalClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Optional;

@Configuration
public class FileReputationConfig {
    /** On only when virustotal.enabled=true AND VT_API_KEY is set; otherwise lookups are skipped. */
    @Bean
    FileReputationClient fileReputationClient(
            @Value("${fraudshield.virustotal.enabled:false}") boolean enabled,
            @Value("${fraudshield.virustotal.api-key:}") String key,
            @Value("${fraudshield.virustotal.base-url:https://www.virustotal.com/api/v3}") String base,
            @Value("${fraudshield.virustotal.max-per-minute:4}") int perMinute) {
        if (!enabled || key == null || key.isBlank()) return sha -> Optional.empty();
        return new VirusTotalClient(base, key, perMinute);
    }
}
