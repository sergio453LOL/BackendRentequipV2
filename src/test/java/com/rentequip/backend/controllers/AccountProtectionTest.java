package com.rentequip.backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentequip.backend.entities.EquipmentCategory;
import com.rentequip.backend.repositories.EquipmentCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Protections around accounts and companies, end to end: login attempts are limited, the public identity
 * lookups are limited, a deactivated company really leaves the marketplace, only a platform admin creates
 * bare companies or grants the verified badge, and a machine with history is not deleted.
 * The test properties set 5 failures per email and 3 identity lookups per IP.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountProtectionTest {

    private static final String PASSWORD = "Password123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger(300);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentCategoryRepository categoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long categoryId;

    @BeforeEach
    void setUp() {
        EquipmentCategory category = new EquipmentCategory();
        category.setName("Proteccion " + System.nanoTime());
        categoryId = categoryRepository.save(category).getId();
    }

    @Test
    void tooManyWrongPasswordsLockTheEmailForAWhileEvenWithTheRightOne() throws Exception {
        String email = "locked" + SEQUENCE.getAndIncrement() + "@protect.pe";
        register(email);
        String other = "free" + SEQUENCE.getAndIncrement() + "@protect.pe";
        register(other);

        for (int i = 0; i < 5; i++) {
            login(email, "Wrong12345").andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists("Retry-After"));
        // Another account from the same place is not affected.
        login(other, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void thePublicIdentityLookupsAreLimitedPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/identity/dni/{dni}", "4567890" + i))
                    .andExpect(status().is(not(429)));
        }
        mockMvc.perform(get("/api/v1/identity/dni/{dni}", "45678909"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void aDeactivatedCompanyLeavesTheMarketplace() throws Exception {
        JsonNode owner = register("owner" + SEQUENCE.getAndIncrement() + "@protect.pe");
        JsonNode renter = register("renter" + SEQUENCE.getAndIncrement() + "@protect.pe");
        String name = "Grua retirada " + System.nanoTime();
        long equipmentId = publish(owner, name);

        call(delete("/api/v1/companies/{id}", owner.get("companyId").asLong()), owner, null)
                .andExpect(status().isNoContent());

        // Its users can no longer sign in or keep the session going.
        login(owner.get("email").asText(), PASSWORD).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(owner.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized());

        // Its machines are out of the catalogue and cannot be booked.
        mockMvc.perform(get("/api/v1/equipment/search").param("q", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        call(post("/api/v1/reservations"), renter, "{\"equipmentId\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\"}"
                .formatted(equipmentId, LocalDate.now().plusDays(2), LocalDate.now().plusDays(3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_UNAVAILABLE"));
    }

    @Test
    void onlyAPlatformAdminCreatesBareCompaniesAndGrantsTheVerifiedBadge() throws Exception {
        JsonNode admin = register("admin" + SEQUENCE.getAndIncrement() + "@protect.pe");
        JsonNode platform = register("platform@rentequip.test");
        long companyId = admin.get("companyId").asLong();
        String bareCompany = """
                {"name":"Empresa sin usuarios","taxId":"2099%07d","email":"bare%d@protect.pe","city":"Lima",
                 "latitude":-12.05,"longitude":-77.04}""";

        int n = SEQUENCE.getAndIncrement();
        call(post("/api/v1/companies"), admin, bareCompany.formatted(n, n))
                .andExpect(status().isForbidden());
        call(post("/api/v1/companies"), platform, bareCompany.formatted(n, n))
                .andExpect(status().isCreated());

        // A company ADMIN cannot give itself the badge; the platform admin grants and withdraws it.
        call(post("/api/v1/companies/{id}/verification", companyId), admin, null)
                .andExpect(status().isForbidden());
        call(post("/api/v1/companies/{id}/verification", companyId), platform, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
        call(get("/api/v1/companies/{id}", companyId), admin, null)
                .andExpect(jsonPath("$.verified").value(true));
        call(delete("/api/v1/companies/{id}/verification", companyId), platform, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void aMachineWithHistoryIsKeptAndOneWithoutHistoryIsDeleted() throws Exception {
        JsonNode owner = register("fleet" + SEQUENCE.getAndIncrement() + "@protect.pe");
        JsonNode renter = register("client" + SEQUENCE.getAndIncrement() + "@protect.pe");
        long rented = publish(owner, "Excavadora con historial " + System.nanoTime());
        long unused = publish(owner, "Excavadora sin uso " + System.nanoTime());

        long reservationId = objectMapper.readTree(call(post("/api/v1/reservations"), renter,
                        "{\"equipmentId\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\"}"
                                .formatted(rented, LocalDate.now().plusDays(2), LocalDate.now().plusDays(3)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        call(post("/api/v1/reservations/{id}/cancellation", reservationId), renter, null)
                .andExpect(status().isOk());

        call(delete("/api/v1/equipment/{id}", rented), owner, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_HAS_HISTORY"));
        call(delete("/api/v1/equipment/{id}", unused), owner, null)
                .andExpect(status().isNoContent());
    }

    private long publish(JsonNode owner, String name) throws Exception {
        String body = """
                {"name":"%s","brand":"Caterpillar","model":"320","dailyRate":500,"securityDeposit":0,
                 "city":"Lima","latitude":-12.05,"longitude":-77.04,"categoryIds":[%d]}""".formatted(name, categoryId);
        return objectMapper.readTree(call(post("/api/v1/equipment"), owner, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, JsonNode session, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.get("accessToken").asText());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private JsonNode register(String email) throws Exception {
        String taxId = "2098%07d".formatted(SEQUENCE.getAndIncrement());
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
