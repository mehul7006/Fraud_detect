package com.fraudshield;

import com.fraudshield.config.AdminBootstrap;
import com.fraudshield.repo.Repositories.UrlFeedbackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Roadmap 4.3 (admin blocklist + CSV import) and 4.4 (false-positive reports). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "fraudshield.admin-email=boss@t.com")
class AdminFeedbackIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired AdminBootstrap bootstrap;
    @Autowired UrlFeedbackRepository feedbackRepo;

    private String register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"));
        return login(email);
    }

    private String login(String email) throws Exception {
        String b = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + b.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private String admin() throws Exception {
        register("boss@t.com");
        bootstrap.promote();
        return login("boss@t.com");
    }

    private String json(String k, String v) { return "{\"kind\":\"" + k + "\",\"value\":\"" + v + "\",\"reason\":\"test\"}"; }

    @Test
    void normalUserGets403OnAdminEndpointsWithJsonBody() throws Exception {
        String user = register(UUID.randomUUID() + "@t.com");
        mvc.perform(get("/api/v1/admin/blocklist").header("Authorization", user))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/v1/admin/blocklist")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminAddsDomainAndNextScanIsMalicious() throws Exception {
        String adm = admin();
        mvc.perform(post("/api/v1/scan/url").header("Authorization", adm).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://new-scam-one.com/\"}")).andExpect(jsonPath("$.score", lessThan(100)));
        mvc.perform(post("/api/v1/admin/blocklist").header("Authorization", adm).contentType(MediaType.APPLICATION_JSON)
            .content(json("DOMAIN", "HTTPS://new-scam-one.com/x"))).andExpect(status().isCreated())
            .andExpect(jsonPath("$.value").value("new-scam-one.com"));
        mvc.perform(post("/api/v1/scan/url").header("Authorization", adm).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://new-scam-one.com/\"}"))
            .andExpect(jsonPath("$.verdict").value("MALICIOUS")).andExpect(jsonPath("$.score").value(100));
        mvc.perform(post("/api/v1/admin/blocklist").header("Authorization", adm).contentType(MediaType.APPLICATION_JSON)
            .content(json("DOMAIN", "new-scam-one.com"))).andExpect(status().isConflict());
    }

    @Test
    void csvImportAddsThreeDomains() throws Exception {
        String adm = admin();
        String csv = "kind,value,reason\nDOMAIN,csv-a.test,x\nDOMAIN,csv-b.test,x\nDOMAIN,csv-c.test,x\nbad line\n";
        mvc.perform(multipart("/api/v1/admin/blocklist/import")
                .file(new MockMultipartFile("file", "b.csv", "text/csv", csv.getBytes())).header("Authorization", adm))
            .andExpect(status().isOk()).andExpect(jsonPath("$.added").value(3)).andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void feedbackStoresHostOnlyRejectsDuplicateAndAdminCanCloseToBlocklist() throws Exception {
        String adm = admin();
        String user = register(UUID.randomUUID() + "@t.com");
        String body = "{\"url\":\"https://Reported-Site.test/secret/path?token=abc123\",\"kind\":\"FALSE_NEGATIVE\",\"note\":\"scam\"}";
        mvc.perform(post("/api/v1/scan/url/feedback").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
            .content(body)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/scan/url/feedback").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
            .content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/scan/url/feedback").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"a.test\",\"kind\":\"NOPE\"}")).andExpect(status().isBadRequest());
        assertTrue(feedbackRepo.findAll().stream().allMatch(f -> !f.getHost().contains("/") && !f.getHost().contains("token")));
        mvc.perform(get("/api/v1/admin/feedback").header("Authorization", user)).andExpect(status().isForbidden());

        String list = mvc.perform(get("/api/v1/admin/feedback?status=OPEN").header("Authorization", adm))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.host=='reported-site.test')]", hasSize(1)))
            .andReturn().getResponse().getContentAsString();
        String id = list.replaceAll(".*\\{\"id\":(\\d+),\"host\":\"reported-site\\.test\".*", "$1");
        mvc.perform(post("/api/v1/admin/feedback/" + id + "/close").header("Authorization", adm)
            .contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"ADD_TO_BLOCKLIST\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(post("/api/v1/scan/url").header("Authorization", adm).contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://reported-site.test/\"}")).andExpect(jsonPath("$.score").value(100));
    }
}
