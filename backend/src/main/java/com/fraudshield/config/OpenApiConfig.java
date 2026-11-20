package com.fraudshield.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API contract served at /v3/api-docs (JSON) and /swagger-ui.html in dev. Disabled in prod. */
@Configuration
public class OpenApiConfig {
    public static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI fraudShieldOpenApi() {
        return new OpenAPI()
                .info(new Info().title("FraudShield API").version("v1")
                        .description("Backend for the FraudShield Android anti-fraud app. All errors use the ErrorBody shape."))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
