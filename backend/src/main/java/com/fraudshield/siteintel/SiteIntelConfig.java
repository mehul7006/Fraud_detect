package com.fraudshield.siteintel;

import com.fraudshield.lookalike.LookalikeService;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 10.5-10.10 settings (fraudshield.urlscan.deep.*). Off in tests: the integration tests must not need the internet. */
@Configuration
public class SiteIntelConfig {
    @Bean
    @ConditionalOnProperty(name = "fraudshield.urlscan.deep.enabled", havingValue = "true", matchIfMissing = true)
    public SiteIntel siteIntel(LookalikeService lookalike,
                               @Value("${fraudshield.urlscan.deep.budget-ms:2700}") long budgetMs,
                               @Value("${fraudshield.urlscan.deep.rdap-timeout-ms:1500}") long rdapMs,
                               @Value("${fraudshield.urlscan.deep.connect-timeout-ms:1500}") int connectMs,
                               @Value("${fraudshield.urlscan.deep.max-page-bytes:1048576}") int maxBytes,
                               @Value("${fraudshield.urlscan.deep.rdap-bootstrap:https://data.iana.org/rdap/dns.json}") String bootstrap) {
        return new SiteIntel(lookalike, new DomainIntelService(bootstrap, Duration.ofMillis(rdapMs)),
                new SafeHttp(AddressGuard.strict(), connectMs, maxBytes, null), Duration.ofMillis(budgetMs));
    }
}
