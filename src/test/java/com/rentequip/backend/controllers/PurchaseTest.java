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

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rent with an option to buy, end to end: the owner lists the machine for sale with a rent credit, the
 * renter asks to buy from a confirmed and paid rental, the owner accepts, the renter pays the rest and
 * the machine is sold.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PurchaseTest {

    private static final String PASSWORD = "Password123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger(500);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentCategoryRepository categoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String ownerToken;
    private String renterToken;
    private long equipmentId;

    @BeforeEach
    void setUp() throws Exception {
        int n = SEQUENCE.getAndIncrement();
        JsonNode owner = register("207100%05d".formatted(n), "owner" + n + "@purchase.pe");
        JsonNode renter = register("208100%05d".formatted(n), "renter" + n + "@purchase.pe");
        ownerToken = owner.get("accessToken").asText();
        renterToken = renter.get("accessToken").asText();

        // Categories are shared by every company and only a platform admin changes them through the API.
        EquipmentCategory category = new EquipmentCategory();
        category.setName("Rodillos " + n);
        long categoryId = categoryRepository.save(category).getId();

        // 900 a day with no deposit, so three days of rent are exactly 2700.
        equipmentId = readId(mockMvc.perform(post("/api/v1/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rodillo Dynapac CA250 %d","brand":"Dynapac","model":"CA250",
                                 "dailyRate":900,"securityDeposit":0,"city":"Lima",
                                 "latitude":-12.05,"longitude":-77.05,"categoryIds":[%d],"ownerCompanyId":%d}"""
                                .formatted(n, categoryId, owner.get("companyId").asLong())))
                .andExpect(status().isCreated()));
    }

    @Test
    void aRenterBuysTheMachineItRentedWithPartOfTheRentCredited() throws Exception {
        putUpForSale("50000", 50);
        long reservationId = confirmedPaidReservation();

        mockMvc.perform(get("/api/v1/reservations/{id}/purchase-quote", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.rentPaid").value(2700.0))
                .andExpect(jsonPath("$.rentCredit").value(1350.0))
                .andExpect(jsonPath("$.finalPrice").value(48650.0));

        long purchaseId = readId(mockMvc.perform(post("/api/v1/reservations/{id}/purchase-requests", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")));

        mockMvc.perform(post("/api/v1/reservations/{id}/purchase-requests", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/purchase-requests/{id}/payment", purchaseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\":\"tok_visa\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/purchase-requests/{id}/acceptance", purchaseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/purchase-requests/{id}/acceptance", purchaseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(post("/api/v1/purchase-requests/{id}/payment", purchaseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\":\"tok_visa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.finalPrice").value(48650.0));

        mockMvc.perform(get("/api/v1/equipment/{id}", equipmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(jsonPath("$.status").value("SOLD"));

        mockMvc.perform(get("/api/v1/purchase-requests").param("role", "OWNER")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(jsonPath("$.content[0].id").value(purchaseId))
                .andExpect(jsonPath("$.content[0].status").value("PAID"));
    }

    @Test
    void anotherRentersPendingReservationBlocksTheSale() throws Exception {
        putUpForSale("50000", 50);
        long reservationId = confirmedPaidReservation();
        long purchaseId = readId(mockMvc.perform(post("/api/v1/reservations/{id}/purchase-requests", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(status().isCreated()));

        int n = SEQUENCE.getAndIncrement();
        String otherRenter = register("208200%05d".formatted(n), "other" + n + "@purchase.pe").get("accessToken").asText();
        mockMvc.perform(post("/api/v1/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherRenter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentId\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\"}".formatted(equipmentId,
                                LocalDate.now().plusDays(20), LocalDate.now().plusDays(22))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/purchase-requests/{id}/acceptance", purchaseId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MACHINE_HAS_OTHER_RENTALS"));
    }

    @Test
    void aMachineThatIsNotForSaleCannotBeBought() throws Exception {
        long reservationId = confirmedPaidReservation();

        mockMvc.perform(get("/api/v1/reservations/{id}/purchase-quote", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reason").value("NOT_FOR_SALE"));

        mockMvc.perform(post("/api/v1/reservations/{id}/purchase-requests", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnconfirmedRentalCannotBuyYetAndOnlyTheOwnerSetsTheTerms() throws Exception {
        mockMvc.perform(put("/api/v1/equipment/{id}/sale", equipmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salePrice\":1,\"rentCreditPercent\":100}"))
                .andExpect(status().isForbidden());

        putUpForSale("50000", 50);
        long reservationId = createReservation();

        mockMvc.perform(get("/api/v1/reservations/{id}/purchase-quote", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reason").value("RESERVATION_STATUS"));
    }

    private void putUpForSale(String price, int creditPercent) throws Exception {
        mockMvc.perform(put("/api/v1/equipment/{id}/sale", equipmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salePrice\":%s,\"rentCreditPercent\":%d}".formatted(price, creditPercent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentCreditPercent").value(creditPercent));
    }

    private long createReservation() throws Exception {
        return readId(mockMvc.perform(post("/api/v1/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentId\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\"}".formatted(equipmentId,
                                LocalDate.now().plusDays(3), LocalDate.now().plusDays(5))))
                .andExpect(status().isCreated()));
    }

    private long confirmedPaidReservation() throws Exception {
        long reservationId = createReservation();
        mockMvc.perform(post("/api/v1/reservations/{id}/payments", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\":\"tok_visa\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/reservations/{id}/confirmation", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());
        return reservationId;
    }

    private JsonNode register(String taxId, String email) throws Exception {
        String payload = """
                {"company":{"name":"Empresa %s","taxId":"%s","email":"%s","city":"Lima",
                  "latitude":-12.0463731,"longitude":-77.0427934},
                 "firstName":"Ana","lastName":"Paz","email":"%s","password":"%s"}"""
                .formatted(taxId, taxId, email, email, PASSWORD);
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long readId(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
