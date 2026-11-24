package com.fraudshield;

import com.fraudshield.lookalike.LookalikeService;
import com.fraudshield.repo.Repositories.BrandRepository;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Phase 10 against real PostgreSQL: brands table (10.1) and the scan response (10.9). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Phase10IT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired BrandRepository brands;
    @Autowired LookalikeService lookalike;
    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");

    private ResultActions postJson(String path, String token, String json) throws Exception {
        var b = post(path).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mvc.perform(b);
    }

    private String user() throws Exception {
        String body = postJson("/api/v1/auth/register", null,
                "{\"email\":\"" + UUID.randomUUID() + "@t.com\",\"password\":\"password123\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Matcher m = TOKEN.matcher(body);
        if (!m.find()) throw new AssertionError(body);
        return m.group(1);
    }

    @Test
    void brands_table_is_seeded_and_used() {
        assertTrue(brands.count() >= 200, "brands: " + brands.count());
        brands.findAll().forEach(b -> assertFalse(b.getOfficialDomains().isBlank(), b.getName()));
        assertEquals(brands.count(), lookalike.registry().size(), "engine reads the table");
        assertEquals("Flipkart", lookalike.registry().officialBrand("flipkart.com").orElseThrow().name());
    }

    @Test
    void fake_flipkart_link_names_the_real_site() throws Exception {
        String t = user();
        postJson("/api/v1/scan/url", t, "{\"url\":\"https://fleepkart.com/big-billion-days\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("MALICIOUS"))
                .andExpect(jsonPath("$.score", greaterThanOrEqualTo(80)))
                .andExpect(jsonPath("$.impersonatedBrand").value("Flipkart"))
                .andExpect(jsonPath("$.officialUrl").value("https://www.flipkart.com"))
                .andExpect(jsonPath("$.reasonCodes", hasItem("LOOKALIKE_BRAND")))
                .andExpect(jsonPath("$.reasons[0]", containsString("Looks like Flipkart")))
                // 11.4 / 11.6: model score and the "why" in English + Hindi
                .andExpect(jsonPath("$.mlScore").isNumber())
                .andExpect(jsonPath("$.explanation.en", startsWith("This link pretends to be Flipkart")))
                .andExpect(jsonPath("$.explanation.hi", containsString("Flipkart")))
                .andExpect(jsonPath("$.explanation.source").value("template"));
        // 11.10: the threat history keeps the explanation
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/threats")
                        .header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$[0].explanationEn", startsWith("This link pretends to be Flipkart")))
                .andExpect(jsonPath("$[0].explanationHi").isString());
        postJson("/api/v1/scan/url", t, "{\"url\":\"https://www.flipkart.com/offers\"}")
                .andExpect(jsonPath("$.verdict").value("SAFE"))
                .andExpect(jsonPath("$.impersonatedBrand").doesNotExist())
                .andExpect(jsonPath("$.explanation").doesNotExist())
                .andExpect(jsonPath("$.mlScore").doesNotExist());
        postJson("/api/v1/scan/url", t, "{\"url\":\"https://www.flickr.com/photos\"}")
                .andExpect(jsonPath("$.verdict").value("SAFE"));
    }
}
