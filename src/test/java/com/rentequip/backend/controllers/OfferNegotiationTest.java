package com.rentequip.backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentequip.backend.entities.EquipmentCategory;
import com.rentequip.backend.repositories.EquipmentCategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A full negotiation: the renter proposes, the owner counters, the renter accepts and books at the agreed
 * price, with a notification for the other side at every turn and the inbox actions on the bell.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OfferNegotiationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentCategoryRepository categoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void renterAndOwnerAgreeOnAPriceAndTheReservationChargesIt() throws Exception {
        JsonNode owner = register("dueno");
        JsonNode renter = register("cliente");
        String ownerToken = owner.get("accessToken").asText();
        String renterToken = renter.get("accessToken").asText();
        long equipmentId = publishEquipment(ownerToken, owner.get("companyId").asLong());

        // The renter proposes 400 against a published 500.
        JsonNode offer = json(call(HttpMethod.POST, "/api/v1/offers", renterToken,
                "{\"equipmentId\":%d,\"dailyRate\":400,\"message\":\"Por 2 semanas\"}".formatted(equipmentId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")));
        long offerId = offer.get("id").asLong();
        call(HttpMethod.GET, "/api/v1/notifications/unread-count", ownerToken, null)
                .andExpect(jsonPath("$.unread").value(1));
        call(HttpMethod.GET, "/api/v1/notifications", ownerToken, null)
                .andExpect(jsonPath("$.content[0].type").value("OFFER_RECEIVED"))
                .andExpect(jsonPath("$.content[0].link").value("/offers?role=OWNER"));

        // One open negotiation per machine, and only the owner answers a pending offer.
        call(HttpMethod.POST, "/api/v1/offers", renterToken, "{\"equipmentId\":%d,\"dailyRate\":350}".formatted(equipmentId))
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/api/v1/offers/%d/acceptance".formatted(offerId), renterToken, null)
                .andExpect(status().isBadRequest());

        // The owner counters with 450; the renter hears about it and accepts.
        call(HttpMethod.POST, "/api/v1/offers/%d/counter".formatted(offerId), ownerToken, "{\"dailyRate\":450}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COUNTERED"));
        call(HttpMethod.GET, "/api/v1/notifications", renterToken, null)
                .andExpect(jsonPath("$.content[0].type").value("OFFER_COUNTERED"));
        call(HttpMethod.POST, "/api/v1/offers/%d/acceptance".formatted(offerId), renterToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.agreedDailyRate").value(450));

        // Booking with the offer charges 450 a day; the offer cannot be used twice.
        LocalDate start = LocalDate.now().plusDays(20);
        String booking = "{\"equipmentId\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\",\"offerId\":%d}"
                .formatted(equipmentId, start, start.plusDays(1), offerId);
        call(HttpMethod.POST, "/api/v1/reservations", renterToken, booking)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dailyRate").value(450))
                .andExpect(jsonPath("$.rentalAmount").value(900));
        String again = "{\"equipmentId\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\",\"offerId\":%d}"
                .formatted(equipmentId, start.plusDays(5), start.plusDays(6), offerId);
        call(HttpMethod.POST, "/api/v1/reservations", renterToken, again)
                .andExpect(status().isBadRequest());

        // The owner's bell: the offer, the acceptance and the booking request; then the bulk actions.
        call(HttpMethod.GET, "/api/v1/notifications/unread-count", ownerToken, null)
                .andExpect(jsonPath("$.unread").value(3));
        call(HttpMethod.POST, "/api/v1/notifications/read-all", ownerToken, null).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/v1/notifications/unread-count", ownerToken, null)
                .andExpect(jsonPath("$.unread").value(0));
        call(HttpMethod.DELETE, "/api/v1/notifications", ownerToken, null).andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/v1/notifications", ownerToken, null)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anOfferAboveThePublishedRateIsRefused() throws Exception {
        JsonNode owner = register("alto");
        JsonNode renter = register("bajo");
        long equipmentId = publishEquipment(owner.get("accessToken").asText(), owner.get("companyId").asLong());

        call(HttpMethod.POST, "/api/v1/offers", renter.get("accessToken").asText(),
                "{\"equipmentId\":%d,\"dailyRate\":600}".formatted(equipmentId))
                .andExpect(status().isBadRequest());
    }

    private long publishEquipment(String token, long companyId) throws Exception {
        EquipmentCategory category = new EquipmentCategory();
        category.setName("Negociables " + System.nanoTime());
        long categoryId = categoryRepository.save(category).getId();
        String body = """
                {"name":"Excavadora de prueba","brand":"Caterpillar","model":"320","dailyRate":500,
                 "securityDeposit":100,"city":"Lima","latitude":-12.05,"longitude":-77.04,
                 "categoryIds":[%d],"ownerCompanyId":%d,"usageHours":3200}""".formatted(categoryId, companyId);
        return json(call(HttpMethod.POST, "/api/v1/equipment", token, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode register(String prefix) throws Exception {
        String id = String.valueOf(System.nanoTime()).substring(0, 11);
        String email = prefix + id + "@test.pe";
        String payload = """
                {"company":{"name":"Empresa %s","taxId":"%s","email":"%s","city":"Lima",
                  "latitude":-12.0463731,"longitude":-77.0427934},
                 "firstName":"Ana","lastName":"Paz","email":"%s","password":"Password123"}"""
                .formatted(id, id, email, email);
        return json(mockMvc.perform(request(HttpMethod.POST, "/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()));
    }

    private ResultActions call(HttpMethod method, String url, String token, String body) throws Exception {
        var builder = request(method, url).header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
