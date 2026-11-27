package com.rentequip.backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one-click demo enters as the account in rentequip.demo-login.email without a password, and that
 * shared account cannot change what would lock the next visitor out: password, two-step verification,
 * profile or company. Everything else (browsing, reserving) works as for any user.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoLoginTest {

    private static final String DEMO_EMAIL = "visitor@demo-login.test";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void theDemoEntranceSignsInAsTheSharedAccountWhichCannotChangeItsSettings() throws Exception {
        String password = "Password123";
        JsonNode registered = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":{"name":"Constructora Visita SAC","taxId":"20655555553","email":"%s",
                                  "city":"Lima","latitude":-12.0463731,"longitude":-77.0427934},
                                 "firstName":"Demo","lastName":"Visita","email":"%s","password":"%s"}"""
                                .formatted(DEMO_EMAIL, DEMO_EMAIL, password)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        JsonNode demo = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEMO_EMAIL))
                .andReturn().getResponse().getContentAsString());
        String bearer = "Bearer " + demo.get("accessToken").asText();

        // Using the product works, and the profile says it is the demo account (the web shows a banner).
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoAccount").value(true));

        // Nothing it publishes would reach the public catalogue: listing changes are refused too.
        mockMvc.perform(delete("/api/v1/equipment/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEMO_ACCOUNT_LOCKED"));

        // Only a platform admin resets it.
        mockMvc.perform(post("/api/v1/admin/demo/reset").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        JsonNode admin = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":{"name":"Plataforma Reset SAC","taxId":"20666666667","email":"reset@rentequip.test",
                                  "city":"Lima","latitude":-12.0463731,"longitude":-77.0427934},
                                 "firstName":"Ana","lastName":"Reset","email":"reset@rentequip.test","password":"Password123"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/admin/demo/reset")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations").value(0));

        // Locking the next visitor out does not.
        mockMvc.perform(post("/api/v1/users/me/password").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"Another123\"}".formatted(password)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEMO_ACCOUNT_LOCKED"));
        mockMvc.perform(post("/api/v1/users/me/two-factor/setup").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DEMO_ACCOUNT_LOCKED"));
        mockMvc.perform(patch("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/companies/{id}", registered.get("companyId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Otra\"}"))
                .andExpect(status().isForbidden());

        // The password still works for the team, unchanged.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(DEMO_EMAIL, password)))
                .andExpect(status().isOk());
    }
}
