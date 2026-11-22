package com.fraudshield;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 8.2: short access tokens, rotating refresh tokens with re-use detection, logout that rejects old tokens. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthTokensIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private ResultActions post_(String path, String bearer, String body) throws Exception {
        var b = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return mvc.perform(b);
    }

    private JsonNode body(ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }

    private String email;
    private JsonNode register() throws Exception {
        email = UUID.randomUUID() + "@t.com";
        return body(post_("/api/v1/auth/register", null, "{\"email\":\"" + email + "\",\"password\":\"password123\"}")
                .andExpect(status().isOk()));
    }

    private ResultActions refresh(String rt) throws Exception {
        return post_("/api/v1/auth/refresh", null, "{\"refreshToken\":\"" + rt + "\"}");
    }

    private ResultActions me(String access) throws Exception {
        return mvc.perform(get("/api/v1/threats").header("Authorization", "Bearer " + access));
    }

    @Test
    void loginGivesShortAccessTokenPlusRefreshToken() throws Exception {
        JsonNode t = register();
        assertFalse(t.get("token").asText().isBlank());
        assertTrue(t.get("refreshToken").asText().length() >= 40);
        assertEquals(15 * 60, t.get("expiresIn").asLong());
        me(t.get("token").asText()).andExpect(status().isOk());
    }

    @Test
    void refreshRotates_andReusingAnOldRefreshTokenKillsTheWholeLogin() throws Exception {
        JsonNode first = register();
        JsonNode second = body(refresh(first.get("refreshToken").asText()).andExpect(status().isOk()));
        assertNotEquals(first.get("refreshToken").asText(), second.get("refreshToken").asText());
        me(second.get("token").asText()).andExpect(status().isOk());

        // someone replays the first (already used) refresh token -> treated as stolen
        refresh(first.get("refreshToken").asText()).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already used")));
        // ...and the legitimate newer one of the same login is revoked too
        refresh(second.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    @Test
    void afterLogout_oldAccessAndRefreshTokensAreRejected_butANewLoginWorksAtOnce() throws Exception {
        JsonNode t = register();
        String access = t.get("token").asText();
        post_("/api/v1/auth/logout", access, "{}").andExpect(status().isNoContent());
        me(access).andExpect(status().isUnauthorized());
        refresh(t.get("refreshToken").asText()).andExpect(status().isUnauthorized());
        JsonNode again = body(post_("/api/v1/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"password123\"}")
                .andExpect(status().isOk()));
        me(again.get("token").asText()).andExpect(status().isOk());
    }

    @Test
    void backgroundSessionIsIndependent_andNeedsALogin() throws Exception {
        post_("/api/v1/auth/background-session", null, "{}").andExpect(status().isUnauthorized());
        JsonNode app = register();
        JsonNode bg = body(post_("/api/v1/auth/background-session", app.get("token").asText(), "{}").andExpect(status().isOk()));
        // the app rotating its own refresh token does not disturb the background scanner's
        refresh(app.get("refreshToken").asText()).andExpect(status().isOk());
        JsonNode bg2 = body(refresh(bg.get("refreshToken").asText()).andExpect(status().isOk()));
        me(bg2.get("token").asText()).andExpect(status().isOk());
    }

    @Test
    void garbageRefreshTokenIs401_notA500() throws Exception {
        refresh("not-a-real-token").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        post_("/api/v1/auth/refresh", null, "{}").andExpect(status().isBadRequest());
    }
}
