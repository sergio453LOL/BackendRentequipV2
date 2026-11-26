package com.rentequip.backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentequip.backend.entities.EquipmentCategory;
import com.rentequip.backend.repositories.EquipmentCategoryRepository;
import com.rentequip.backend.repositories.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One company must never read or write what belongs to another: its machines, the reservations of its
 * machines, its staff, nor the categories every company shares. Each test signs in two unrelated companies
 * and checks that the second one is turned away with 403 while the first one still gets its own data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CrossTenantAccessTest {

    private static final String PASSWORD = "Password123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger(900);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentCategoryRepository categoryRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode owner;
    private JsonNode outsider;
    private long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        int n = SEQUENCE.getAndIncrement();
        owner = register("209100%05d".formatted(n), "owner" + n + "@tenants.pe");
        outsider = register("209200%05d".formatted(n), "outsider" + n + "@tenants.pe");

        EquipmentCategory category = new EquipmentCategory();
        category.setName("Inquilinos " + n);
        categoryId = categoryRepository.save(category).getId();
    }

    @Test
    void aCompanyCannotPublishEquipmentInAnotherCompanysName() throws Exception {
        call(post("/api/v1/equipment"), outsider, equipmentJson("Excavadora ajena", owner.get("companyId").asLong()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));
        assertThat(equipmentRepository.findByOwnerId(owner.get("companyId").asLong(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isZero();

        // Its own company, or no owner at all, publishes for the caller.
        long ownId = publish(outsider, equipmentJson("Excavadora propia", outsider.get("companyId").asLong()));
        long implicitId = publish(outsider, equipmentJson("Excavadora sin dueño", null));
        for (long id : new long[]{ownId, implicitId}) {
            call(get("/api/v1/equipment/{id}", id), outsider, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ownerCompanyId").value(outsider.get("companyId").asLong()));
        }
    }

    @Test
    void aCompanyAdminCannotChangeTheSharedCategories() throws Exception {
        String body = "{\"name\":\"Categoria basura\"}";
        call(post("/api/v1/equipment-categories"), outsider, body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        call(put("/api/v1/equipment-categories/{id}", categoryId), outsider, body)
                .andExpect(status().isForbidden());
        call(delete("/api/v1/equipment-categories/{id}", categoryId), outsider, null)
                .andExpect(status().isForbidden());

        assertThat(categoryRepository.findById(categoryId)).get()
                .extracting(EquipmentCategory::getName).asString().startsWith("Inquilinos ");
        // Reading stays public.
        mockMvc.perform(get("/api/v1/equipment-categories/{id}", categoryId)).andExpect(status().isOk());
    }

    @Test
    void onlyTheOwnerListsTheReservationsOfItsMachine() throws Exception {
        long equipmentId = publish(owner, equipmentJson("Retroexcavadora JCB", null));
        JsonNode renter = register("209300%05d".formatted(SEQUENCE.getAndIncrement()),
                "renter" + System.nanoTime() + "@tenants.pe");
        call(post("/api/v1/reservations"), renter, "{\"equipmentId\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\"}"
                .formatted(equipmentId, LocalDate.now().plusDays(3), LocalDate.now().plusDays(5)))
                .andExpect(status().isCreated());

        call(get("/api/v1/equipment/{id}/reservations", equipmentId), outsider, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));
        // Not even the renter gets the machine's whole history: it sees its own bookings in /reservations.
        call(get("/api/v1/equipment/{id}/reservations", equipmentId), renter, null)
                .andExpect(status().isForbidden());

        call(get("/api/v1/equipment/{id}/reservations", equipmentId), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void usersAreVisibleOnlyInsideTheirCompany() throws Exception {
        long ownerUserId = owner.get("userId").asLong();
        long ownerCompanyId = owner.get("companyId").asLong();

        call(get("/api/v1/users/{id}", ownerUserId), outsider, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));
        call(get("/api/v1/companies/{id}/users", ownerCompanyId), outsider, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));

        call(get("/api/v1/users/{id}", ownerUserId), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(owner.get("email").asText()));
        call(get("/api/v1/companies/{id}/users", ownerCompanyId), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private String equipmentJson(String name, Long ownerCompanyId) {
        return """
                {"name":"%s %d","brand":"Caterpillar","model":"320","dailyRate":500,"securityDeposit":100,
                 "city":"Lima","latitude":-12.05,"longitude":-77.04,"categoryIds":[%d]%s}"""
                .formatted(name, System.nanoTime(), categoryId,
                        ownerCompanyId == null ? "" : ",\"ownerCompanyId\":" + ownerCompanyId);
    }

    private long publish(JsonNode session, String body) throws Exception {
        String response = call(post("/api/v1/equipment"), session, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               JsonNode session, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + session.get("accessToken").asText());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
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
