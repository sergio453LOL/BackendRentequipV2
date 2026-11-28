package com.rentequip.backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentequip.backend.services.assistant.PlatformSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin copilot answers platform admins only, from the live figures. With no model key (as in tests)
 * it answers with the summary of the main figures; the figures it would hand a model carry company names
 * but never their e-mails or tax ids.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAssistantTest {

    private static final String QUESTION = """
            {"messages":[{"role":"user","content":"¿Cuántas empresas esperan verificación?"}]}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlatformSnapshot snapshot;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void onlyAPlatformAdminCanAskAndGetsTheFiguresWithoutContactData() throws Exception {
        String member = register("Constructora Copiloto SAC", "20677777771", "member@copilot.test");
        mockMvc.perform(post("/api/v1/admin/assistant/chat").header(HttpHeaders.AUTHORIZATION, member)
                        .contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isForbidden());

        String admin = register("Plataforma Copiloto SAC", "20688888884", "copilot@rentequip.test");
        mockMvc.perform(post("/api/v1/admin/assistant/chat").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(QUESTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("summary"))
                .andExpect(jsonPath("$.reply").value(containsString("esperando verificación")))
                .andExpect(jsonPath("$.asOf").exists());

        // The last turn has to be the admin's question.
        mockMvc.perform(post("/api/v1/admin/assistant/chat").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"assistant","content":"Hola"}]}"""))
                .andExpect(status().isBadRequest());

        String facts = snapshot.capture().text();
        assertThat(facts).contains("Esperando verificación", "Reservas");
        assertThat(facts).doesNotContain("member@copilot.test", "20677777771");
    }

    private String register(String company, String ruc, String email) throws Exception {
        JsonNode registered = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":{"name":"%s","taxId":"%s","email":"%s",
                                  "city":"Arequipa","latitude":-16.3988,"longitude":-71.5369},
                                 "firstName":"Ana","lastName":"Copiloto","email":"%s","password":"Password123"}"""
                                .formatted(company, ruc, email, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return "Bearer " + registered.get("accessToken").asText();
    }
}
