package com.fraudshield;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Roadmap 2.1: the published OpenAPI document must contain every endpoint listed in docs/04-API-SPEC.md,
 * so the Flutter client and the backend cannot silently drift apart.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiContractIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;

    @Test
    void apiDocs_arePublic_andListEveryDocumentedEndpoint() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FraudShield API"))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/auth/register")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/auth/login")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/devices")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/devices/heartbeat")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/scan/url")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/scan/file")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/scan/apps")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/threats")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/threats/{id}/resolve")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/me")))
                .andExpect(jsonPath("$.paths", hasKey("/api/v1/config/carriers")));
    }

    @Test
    void apiDocs_declareBearerAuth_andPublicAuthEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                // /auth/login must NOT demand a token
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security", hasSize(0)));
    }

    @Test
    void requestSchemas_matchWhatTheAppSends() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.UrlScanRequest.required", hasItem("url")))
                .andExpect(jsonPath("$.components.schemas.FileScanRequest.properties.sha256.maxLength").value(64))
                .andExpect(jsonPath("$.components.schemas.AppInfo.properties", hasKey("notifPerHour")))
                .andExpect(jsonPath("$.components.schemas.DeviceRequest.properties", hasKey("remoteAppsInstalled")))
                .andExpect(jsonPath("$.components.schemas.ScanResponse.properties.verdict.enum",
                        containsInAnyOrder("SAFE", "SUSPICIOUS", "MALICIOUS")));
    }

    @Test
    void swaggerUi_isReachable() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
