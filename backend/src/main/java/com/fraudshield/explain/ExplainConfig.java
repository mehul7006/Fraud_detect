package com.fraudshield.explain;

import com.fraudshield.lookalike.BrandRegistry;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 11.6: ANTHROPIC_API_KEY set -> Claude words explanations (in the background); not set -> templates only. */
@Configuration
public class ExplainConfig {
    @Bean
    public ExplainService explainService(@Value("${fraudshield.explain.anthropic-api-key:}") String key) {
        var brands = BrandRegistry.seedFromClasspath().stream().map(BrandRegistry.Brand::name).collect(Collectors.toSet());
        if (key == null || key.isBlank()) {
            LoggerFactory.getLogger(ExplainConfig.class).info("Explanations: templates only (no ANTHROPIC_API_KEY)");
            return new ExplainService(brands, null);
        }
        ExplainService templates = new ExplainService(brands, null);
        return new ExplainService(brands, new ClaudeExplainer(key, code -> templates.template(
                new ExplainService.Request("SUSPICIOUS", java.util.List.of(code), null)).get("en")));
    }
}
