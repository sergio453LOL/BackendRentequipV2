package com.rentequip.backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentequip.backend.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A session has a fixed lifetime from the login: refreshing renews the access token but hands back a
 * refresh token with the same expiry, so an account is never left signed in indefinitely.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionLifetimeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void refreshingKeepsTheExpiryOfTheSessionOpenedAtLogin() throws Exception {
        JsonNode login = register();
        Instant sessionEnd = expiryOf(login.get("refreshToken").asText());

        assertThat(Duration.between(Instant.now(), sessionEnd)).isBetween(Duration.ofHours(7), Duration.ofHours(8));

        Thread.sleep(1100);
        JsonNode refreshed = refresh(login.get("refreshToken").asText());

        assertThat(expiryOf(refreshed.get("refreshToken").asText())).isEqualTo(sessionEnd);
        assertThat(expiryOf(refreshed.get("accessToken").asText())).isAfter(expiryOf(login.get("accessToken").asText()));
    }

    @Test
    void anAccessTokenCannotBeUsedToRefresh() throws Exception {
        JsonNode login = register();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + login.get("accessToken").asText() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private Instant expiryOf(String token) {
        Claims claims = jwtService.parse(token);
        return claims.getExpiration().toInstant();
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode register() throws Exception {
        String id = String.valueOf(System.nanoTime()).substring(0, 11);
        String email = "session" + id + "@test.pe";
        String payload = """
                {"company":{"name":"Empresa %s","taxId":"%s","email":"%s","city":"Lima",
                  "latitude":-12.0463731,"longitude":-77.0427934},
                 "firstName":"Ana","lastName":"Paz","email":"%s","password":"Password123"}"""
                .formatted(id, id, email, email);
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }
}
