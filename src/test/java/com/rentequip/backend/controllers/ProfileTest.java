package com.rentequip.backend.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The self-service profile: every role edits its own name, phone and photo and changes its password;
 * only an administrator edits the company's logo and public description.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileTest {

    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void anOperatorEditsTheirOwnProfileButNotTheirRole() throws Exception {
        JsonNode admin = register("20610000011", "admin@profile.pe", "Constructora Perfil");
        String operatorToken = createUserAndLogin(admin, "operator@profile.pe", "OPERATOR");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Lucia","phone":"987654321",
                                 "avatarUrl":"http://localhost:8080/uploads/abc.png","role":"ADMIN"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Lucia"))
                .andExpect(jsonPath("$.lastName").value("Diaz"))
                .andExpect(jsonPath("$.avatarUrl").value("http://localhost:8080/uploads/abc.png"))
                .andExpect(jsonPath("$.role").value("OPERATOR"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"","avatarUrl":""}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("operator@profile.pe"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
    }

    @Test
    void anAvatarMustBeAnHttpUrl() throws Exception {
        JsonNode admin = register("20610000022", "admin@avatar.pe", "Constructora Avatar");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"avatarUrl":"javascript:alert(1)"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingThePasswordRequiresTheCurrentOneAndANewValue() throws Exception {
        JsonNode admin = register("20610000033", "admin@password.pe", "Constructora Clave");
        String token = admin.get("accessToken").asText();

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Wrong12345","newPassword":"NuevaClave2026"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}""".formatted(PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_PASSWORD"));

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"NuevaClave2026"}""".formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@password.pe","password":"NuevaClave2026"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void anAdminSetsTheCompanyLogoAndDescriptionAndAManagerCannot() throws Exception {
        JsonNode admin = register("20610000044", "admin@brand.pe", "Constructora Marca");
        long companyId = admin.get("companyId").asLong();

        mockMvc.perform(patch("/api/v1/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"logoUrl":"https://cdn.example.pe/logo.png",
                                 "description":"Alquiler de maquinaria en Lima desde 2012."}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value("https://cdn.example.pe/logo.png"))
                .andExpect(jsonPath("$.description").value("Alquiler de maquinaria en Lima desde 2012."));

        String managerToken = createUserAndLogin(admin, "manager@brand.pe", "MANAGER");
        mockMvc.perform(patch("/api/v1/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Cambio no autorizado"}"""))
                .andExpect(status().isForbidden());
    }

    private String createUserAndLogin(JsonNode admin, String email, String role) throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Luis","lastName":"Diaz","email":"%s",
                                 "password":"%s","role":"%s","companyId":%d}"""
                                .formatted(email, PASSWORD, role, admin.get("companyId").asLong())))
                .andExpect(status().isCreated());

        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private JsonNode register(String taxId, String email, String companyName) throws Exception {
        String payload = """
                {
                  "company": {
                    "name": "%s",
                    "taxId": "%s",
                    "email": "%s",
                    "city": "Lima",
                    "latitude": -12.0463731,
                    "longitude": -77.0427934
                  },
                  "firstName": "Sergio",
                  "lastName": "Rojas",
                  "email": "%s",
                  "password": "%s"
                }""".formatted(companyName, taxId, email, email, PASSWORD);
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }
}
