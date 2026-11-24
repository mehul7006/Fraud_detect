package com.fraudshield;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fraudshield.repo.Repositories.ThreatEventRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 14.6: opt-in cloud message check - refused without consent, nothing stored, nothing logged. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.level.com.fraudshield=DEBUG")
class Phase14IT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired ThreatEventRepository threats;
    @Autowired JdbcTemplate jdbc;

    private String login() throws Exception {
        String email = UUID.randomUUID() + "@t.com";
        String b = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + b.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void consent_required_and_no_trace(CapturedOutput out) throws Exception {
        String t = login();
        String marker = "Zqx" + UUID.randomUUID().toString().substring(0, 6);
        String text = "Dear customer, your SBI account will be blocked today. Update KYC now http://sbi-kyc-" + marker.toLowerCase() + ".top/ " + marker;
        mvc.perform(post("/api/v1/scan/message").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\",\"consent\":false}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/scan/message").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + text + "\"}")).andExpect(status().isBadRequest());

        long threatsBefore = threats.count();
        mvc.perform(post("/api/v1/scan/message").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + text + "\",\"consent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$.category").value("BANK_KYC_BLOCK"))
                .andExpect(jsonPath("$.links[0].verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$.links[0].impersonatedBrand").value("State Bank of India"))
                .andExpect(jsonPath("$.explanation.hi", not(emptyString())));
        mvc.perform(post("/api/v1/scan/message").header("Authorization", t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"482913 is your OTP for Rs 1,250 at Amazon. Do not share this OTP with anyone. - HDFC Bank\",\"consent\":true}"))
                .andExpect(jsonPath("$.verdict").value("SAFE"));

        assertEquals(threatsBefore, threats.count(), "no threat record");
        assertFalse(out.getAll().contains(marker), "message text never in the log");
        // and never in the database: search every text column of every table
        var cols = jdbc.queryForList("select table_name, column_name from information_schema.columns where table_schema='public' and data_type in ('text','character varying')");
        for (var c : cols) {
            Integer n = jdbc.queryForObject("select count(*) from " + c.get("table_name") + " where cast(" + c.get("column_name") + " as text) like ?",
                    Integer.class, "%" + marker + "%");
            assertEquals(0, n, "found in " + c);
        }
    }
}
