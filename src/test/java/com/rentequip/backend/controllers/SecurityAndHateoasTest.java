package com.rentequip.backend.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * End to end checks over the real filter chain: registration mints a token, the token opens protected
 * endpoints, its absence closes them, and the payload carries its hypermedia links.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAndHateoasTest {

    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registrationReturnsATokenThatOpensProtectedEndpoints() throws Exception {
        JsonNode session = register("20600000011", "admin@hateoas.pe", "Constructora Hateoas");
        String token = session.get("accessToken").asText();
        long companyId = session.get("companyId").asLong();

        mockMvc.perform(get("/api/v1/companies/{id}", companyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyId))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.users.href").exists())
                .andExpect(jsonPath("$._links.equipment.href").exists());
    }

    @Test
    void registrationIssuesAccessAndRefreshTokensAndNeverLeaksThePassword() throws Exception {
        JsonNode session = register("20600000022", "admin@tokens.pe", "Constructora Tokens");

        org.assertj.core.api.Assertions.assertThat(session.get("accessToken").asText()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(session.get("refreshToken").asText()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(session.get("tokenType").asText()).isEqualTo("Bearer");
        org.assertj.core.api.Assertions.assertThat(session.has("password")).isFalse();
        org.assertj.core.api.Assertions.assertThat(session.get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void loginWithTheRegisteredCredentialsReturnsAToken() throws Exception {
        register("20600000033", "admin@login.pe", "Constructora Login");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@login.pe","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void loginWithAWrongPasswordIsRejected() throws Exception {
        register("20600000044", "admin@wrongpass.pe", "Constructora Wrong");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@wrongpass.pe","password":"NotTheOne123"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedEndpointWithoutATokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/reservations"));
    }

    @Test
    void protectedEndpointWithAForgedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/reservations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCompanyCannotModifyAnotherCompany() throws Exception {
        JsonNode intruder = register("20600000055", "admin@intruder.pe", "Constructora Intrusa");
        JsonNode victim = register("20600000066", "admin@victim.pe", "Constructora Victima");

        mockMvc.perform(patch("/api/v1/companies/{id}", victim.get("companyId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"999888777"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void anOperatorCannotPublishEquipment() throws Exception {
        JsonNode admin = register("20600000077", "admin@roles.pe", "Constructora Roles");
        String adminToken = admin.get("accessToken").asText();

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ana","lastName":"Perez","email":"operator@roles.pe",
                                 "password":"%s","role":"OPERATOR","companyId":%d}"""
                                .formatted(PASSWORD, admin.get("companyId").asLong())))
                .andExpect(status().isCreated());

        String operatorToken = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"operator@roles.pe","password":"%s"}""".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();

        // A body-less endpoint isolates the role check: bean validation would answer 400 before
        // @PreAuthorize ever runs, hiding the very thing this test is about.
        mockMvc.perform(delete("/api/v1/equipment/{id}", 1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void publicSearchEndpointStaysOpen() throws Exception {
        mockMvc.perform(get("/api/v1/equipment-categories"))
                .andExpect(status().isOk());
    }

    @Test
    void catalogueSearchWorksWithoutTokenOrCoordinates() throws Exception {
        mockMvc.perform(get("/api/v1/equipment/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void onlyAnAdminCanManageCategories() throws Exception {
        JsonNode admin = register("20600000088", "admin@categories.pe", "Constructora Categorias");
        String managerToken = createUserAndLogin(admin, "manager@categories.pe", "MANAGER");

        mockMvc.perform(delete("/api/v1/equipment-categories/{id}", 1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/equipment-categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Compactadoras"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void dashboardReturnsTheMetricsOfTheAuthenticatedCompany() throws Exception {
        JsonNode session = register("20600000099", "admin@dashboard.pe", "Constructora Dashboard");

        mockMvc.perform(get("/api/v1/companies/me/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(session.get("companyId").asLong()))
                .andExpect(jsonPath("$.publishedEquipment").value(0))
                .andExpect(jsonPath("$.activeReservationsAsOwner").value(0))
                .andExpect(jsonPath("$.estimatedRevenue").value(0))
                .andExpect(jsonPath("$.currency").value("PEN"));
    }

    @Test
    void dashboardRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/v1/companies/me/dashboard"))
                .andExpect(status().isUnauthorized());
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

        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }
}
