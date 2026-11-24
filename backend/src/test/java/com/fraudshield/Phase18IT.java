package com.fraudshield;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fraudshield.blocking.BlocklistFileServiceTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 18.2: signed block-list meta + file, per flavour; authenticated; bad variant refused. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Phase18IT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    static final Path DIR;
    static {
        try {
            DIR = Files.createTempDirectory("fs-blocklists");
            Files.write(DIR.resolve("blocklists-web.bin"), BlocklistFileServiceTest.fsbl(7, Map.of("ads.example.com", 1, "evil.top", 3)));
            Files.write(DIR.resolve("blocklists-play.bin"), BlocklistFileServiceTest.fsbl(7, Map.of("evil.top", 3)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) { r.add("fraudshield.blocklists.dir", DIR::toString); }

    @Autowired MockMvc mvc;

    private String login() throws Exception {
        String email = UUID.randomUUID() + "@t.com";
        String b = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + b.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void meta_and_file_per_flavour() throws Exception {
        mvc.perform(get("/api/v1/config/blocklists")).andExpect(status().isUnauthorized());
        String t = login();
        mvc.perform(get("/api/v1/config/blocklists").header("Authorization", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variant").value("play"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.categories.ads").value(0))
                .andExpect(jsonPath("$.signature").isNotEmpty());
        mvc.perform(get("/api/v1/config/blocklists?variant=web").header("Authorization", t))
                .andExpect(jsonPath("$.count").value(2)).andExpect(jsonPath("$.version").value(7));
        byte[] f = mvc.perform(get("/api/v1/config/blocklists/file?variant=web").header("Authorization", t))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals(17 + 2 * 9, f.length);
        assertEquals("FSBL1", new String(f, 0, 5));
        mvc.perform(get("/api/v1/config/blocklists?variant=other").header("Authorization", t)).andExpect(status().isBadRequest());
    }
}
