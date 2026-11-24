package com.fraudshield.ml;

import com.fraudshield.lookalike.LookalikeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 11.4: fraudshield.ml.model = "classpath:models/url-v1" (default) or a file path without extension; empty = off. */
@Configuration
public class MlConfig {
    @Bean
    public MlScoreService mlScoreService(LookalikeService lookalike, @Value("${fraudshield.ml.model:classpath:models/url-v1}") String model) {
        return new MlScoreService(lookalike, model);
    }
}
