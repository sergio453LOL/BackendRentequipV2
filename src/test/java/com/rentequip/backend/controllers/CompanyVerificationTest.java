package com.rentequip.backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentequip.backend.identity.IdentityLookupClient;
import com.rentequip.backend.identity.RucInfo;
import com.rentequip.backend.identity.RucValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The verified badge is not handed out for a RUC alone: SUNAT's good standing only puts the company in line,
 * and a platform admin grants the badge. A bad standing on a later check takes it away. SUNAT is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyVerificationTest {

    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityLookupClient lookupClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aGoodStandingWaitsForThePlatformAdminAndABadOneRemovesTheBadge() throws Exception {
        String ruc = validRuc("2061234567");
        JsonNode admin = register(ruc, "admin" + ruc + "@verify.pe");
        JsonNode platform = register(validRuc("2069876543"), "verifier@rentequip.test");
        when(lookupClient.isConfigured()).thenReturn(true);
        when(lookupClient.findRuc(ruc)).thenReturn(Optional.of(sunat(ruc, "ACTIVO", "HABIDO")));

        // SUNAT says ACTIVO / HABIDO: recorded, but no badge yet.
        call("/api/v1/companies/me/verification", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxStatus").value("ACTIVO / HABIDO"))
                .andExpect(jsonPath("$.verified").value(false));

        call("/api/v1/companies/%d/verification".formatted(admin.get("companyId").asLong()), platform)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        // Later SUNAT reports the RUC suspended: the check fails and the badge is gone.
        when(lookupClient.findRuc(ruc)).thenReturn(Optional.of(sunat(ruc, "SUSPENSION TEMPORAL", "HABIDO")));
        call("/api/v1/companies/me/verification", admin)
                .andExpect(status().isBadRequest());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/companies/{id}", admin.get("companyId").asLong())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.get("accessToken").asText()))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.taxStatus").value("SUSPENSION TEMPORAL / HABIDO"));

        // Nor will the platform admin grant it while SUNAT disagrees.
        call("/api/v1/companies/%d/verification".formatted(admin.get("companyId").asLong()), platform)
                .andExpect(status().isBadRequest());
    }

    private static RucInfo sunat(String ruc, String state, String condition) {
        return new RucInfo(ruc, "CONSTRUCTORA PRUEBA S.A.C.", state, condition, "AV. LIMA 123", "LIMA", "LIMA",
                "LIMA", RucValidator.RucType.LEGAL_ENTITY);
    }

    /** Appends SUNAT's modulo-11 check digit to ten digits. */
    private static String validRuc(String tenDigits) {
        for (int digit = 0; digit <= 9; digit++) {
            if (RucValidator.isValid(tenDigits + digit)) {
                return tenDigits + digit;
            }
        }
        throw new IllegalArgumentException("No check digit for " + tenDigits);
    }

    private ResultActions call(String path, JsonNode session) throws Exception {
        return mockMvc.perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.get("accessToken").asText()));
    }

    private JsonNode register(String taxId, String email) throws Exception {
        String payload = """
                {"company":{"name":"Empresa %s","taxId":"%s","email":"%s","city":"Lima",
                  "latitude":-12.0463731,"longitude":-77.0427934},
                 "firstName":"Ana","lastName":"Paz","email":"%s","password":"%s"}"""
                .formatted(taxId, taxId, email, email, PASSWORD);
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }
}
