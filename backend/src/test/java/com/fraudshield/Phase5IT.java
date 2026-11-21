package com.fraudshield;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Roadmap 5.2 (flags over HTTP) and 5.3 (threat summary). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Phase5IT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;

    private String token() throws Exception {
        String b = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + UUID.randomUUID() + "@t.com\",\"password\":\"password123\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + b.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private ResultActions scanFile(String t, String name, String flagsJson, String sha) throws Exception {
        return mvc.perform(post("/api/v1/scan/file").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"sha256\":\"" + sha + "\",\"fileName\":\"" + name + "\",\"sizeBytes\":90000,\"headerHex\":\"25504446\",\"sourceApp\":\"downloads\""
                + (flagsJson == null ? "" : ",\"flags\":" + flagsJson) + "}"));
    }

    @Test
    void fileWithPdfLaunchFlagIsMalicious() throws Exception {
        String t = token();
        scanFile(t, "bill.pdf", null, "1".repeat(64)).andExpect(status().isOk()).andExpect(jsonPath("$.verdict").value("SAFE"));
        scanFile(t, "bill.pdf", "[\"PDF_LAUNCH\"]", "2".repeat(64)).andExpect(status().isOk())
            .andExpect(jsonPath("$.verdict").value("MALICIOUS")).andExpect(jsonPath("$.action").value("DELETE"));
        scanFile(t, "bill.pdf", "[\"A\",\"B\",\"C\",\"D\",\"E\",\"F\",\"G\",\"H\",\"I\",\"J\",\"K\"]", "3".repeat(64)).andExpect(status().isBadRequest());
    }

    @Test
    void threatSummaryCounts() throws Exception {
        String t = token();
        scanFile(t, "bill.pdf", "[\"PDF_LAUNCH\"]", "4".repeat(64));
        scanFile(t, "invoice.pdf.apk", null, "5".repeat(64));
        mvc.perform(post("/api/v1/scan/url").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"http://example-phish.test/x\"}"));
        mvc.perform(get("/api/v1/threats/summary").header("Authorization", t)).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.open").value(3))
            .andExpect(jsonPath("$.byType.MALICIOUS_FILE").value(2)).andExpect(jsonPath("$.byType.MALICIOUS_LINK").value(1))
            .andExpect(jsonPath("$.byType.CALL_FORWARDING").value(0)).andExpect(jsonPath("$.bySeverity.CRITICAL").value(3));
        String other = token();
        mvc.perform(get("/api/v1/threats/summary").header("Authorization", other)).andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.byType.MALICIOUS_FILE").value(0));
        mvc.perform(get("/api/v1/threats/summary")).andExpect(status().isUnauthorized());
    }
}
