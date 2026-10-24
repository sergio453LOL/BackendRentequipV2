package com.rentequip.backend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checkout (rental charge + deposit hold + refunds), image uploads and the catalogue assistant, end to end
 * through the real filter chain. The assistant runs without an API key here, so it answers with the local
 * catalogue recommender.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutUploadAndAssistantTest {

    private static final String PASSWORD = "Password123";
    private static final AtomicInteger SEQUENCE = new AtomicInteger(100);
    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String ownerToken;
    private String renterToken;
    private long equipmentId;

    @BeforeEach
    void setUp() throws Exception {
        int n = SEQUENCE.getAndIncrement();
        JsonNode owner = register("207000%05d".formatted(n), "owner" + n + "@checkout.pe");
        JsonNode renter = register("208000%05d".formatted(n), "renter" + n + "@checkout.pe");
        ownerToken = owner.get("accessToken").asText();
        renterToken = renter.get("accessToken").asText();

        long categoryId = readId(mockMvc.perform(post("/api/v1/equipment-categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Niveladoras " + n + "\"}"))
                .andExpect(status().isCreated()));

        equipmentId = readId(mockMvc.perform(post("/api/v1/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Motoniveladora CAT 140 %d","brand":"Caterpillar","model":"140 GC",
                                 "dailyRate":900,"securityDeposit":2500,"city":"Lima",
                                 "latitude":-12.05,"longitude":-77.05,"categoryIds":[%d],"ownerCompanyId":%d}"""
                                .formatted(n, categoryId, owner.get("companyId").asLong())))
                .andExpect(status().isCreated()));
    }

    @Test
    void payingChargesTheRentalAndHoldsTheDeposit() throws Exception {
        long reservationId = createReservation(3, 5);

        mockMvc.perform(post("/api/v1/reservations/{id}/payments", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\":\"tok_visa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("RENTAL"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].amount").value(2700.0))
                .andExpect(jsonPath("$[1].type").value("SECURITY_DEPOSIT"))
                .andExpect(jsonPath("$[1].status").value("AUTHORIZED"));

        mockMvc.perform(get("/api/v1/reservations/{id}", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        mockMvc.perform(post("/api/v1/reservations/{id}/payments", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\":\"tok_visa\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OPERATION"));
    }

    @Test
    void aDeclinedCardLeavesTheReservationUnpaid() throws Exception {
        long reservationId = createReservation(10, 11);

        mockMvc.perform(post("/api/v1/reservations/{id}/payments", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\":\"tok_chargeDeclinedInsufficientFunds\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"));

        mockMvc.perform(get("/api/v1/reservations/{id}/payments", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void onlyTheRenterCanPay() throws Exception {
        long reservationId = createReservation(20, 21);

        mockMvc.perform(post("/api/v1/reservations/{id}/payments", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\":\"tok_visa\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancellingAPaidReservationRefundsEverything() throws Exception {
        long reservationId = createReservation(30, 31);
        mockMvc.perform(post("/api/v1/reservations/{id}/payments", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentToken\":\"tok_mastercard\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/reservations/{id}/cancellation", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));

        mockMvc.perform(get("/api/v1/reservations/{id}/payments", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(jsonPath("$[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$[1].status").value("REFUNDED"));
    }

    @Test
    void uploadsAPngAndServesItPublicly() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "foto.png", "image/png", PNG_HEADER);

        String url = objectMapper.readTree(mockMvc.perform(multipart("/api/v1/uploads/images").file(image)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.url", startsWith("http://localhost/uploads/")))
                        .andReturn().getResponse().getContentAsString())
                .get("url").asText();

        mockMvc.perform(get(url.replace("http://localhost", "")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void behindTheHttpsProxyImageUrlsUseHttps() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "foto.png", "image/png", PNG_HEADER);

        mockMvc.perform(multipart("/api/v1/uploads/images").file(image)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "3-237-198-235.sslip.io"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", startsWith("https://3-237-198-235.sslip.io/uploads/")));
    }

    @Test
    void vercelDeploymentsPassTheCorsPreflight() throws Exception {
        mockMvc.perform(options("/api/v1/equipment/search")
                        .header(HttpHeaders.ORIGIN, "https://rentequip.vercel.app")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://rentequip.vercel.app"));

        mockMvc.perform(options("/api/v1/equipment/search")
                        .header(HttpHeaders.ORIGIN, "https://sitio-ajeno.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theCalendarShowsWhenAMachineIsBusyButNotWhoRentedIt() throws Exception {
        createReservation(10, 14);
        LocalDate today = LocalDate.now();

        mockMvc.perform(get("/api/v1/equipment/{id}/blocked-dates", equipmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .param("from", today.toString())
                        .param("until", today.plusDays(30).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].startDate").value(today.plusDays(10).toString()))
                .andExpect(jsonPath("$[0].endDate").value(today.plusDays(14).toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].renterCompanyName").doesNotExist());

        mockMvc.perform(get("/api/v1/equipment/{id}/blocked-dates", equipmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .param("from", today.toString())
                        .param("until", today.plusDays(200).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFilesThatAreNotImagesWhateverTheirDeclaredType() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "foto.png", "image/png", "<script>".getBytes());

        mockMvc.perform(multipart("/api/v1/uploads/images").file(fake)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OPERATION"));
    }

    @Test
    void storedFileNamesCannotEscapeTheUploadsFolder() throws Exception {
        mockMvc.perform(get("/uploads/..%2Fpom.xml"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void assistantRecommendsMachinesForATaskWithoutAnApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user",
                                  "content":"¿Qué equipo me recomiendas para nivelar un terreno en Lima?"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("catalog-search"))
                .andExpect(jsonPath("$.recommendations[0].name", startsWith("Motoniveladora")))
                .andExpect(jsonPath("$.reply").isNotEmpty());
    }

    @Test
    void assistantRejectsAConversationThatDoesNotEndWithTheUser() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"assistant\",\"content\":\"Hola\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bothCompaniesCanDocumentTheHandoverOfAConfirmedReservation() throws Exception {
        long reservationId = createReservation(40, 42);
        MockMultipartFile photo = new MockMultipartFile("file", "entrega.png", "image/png", PNG_HEADER);

        mockMvc.perform(multipart("/api/v1/reservations/{id}/handover-photos", reservationId).file(photo)
                        .param("stage", "DELIVERY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OPERATION"));

        mockMvc.perform(post("/api/v1/reservations/{id}/confirmation", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/v1/reservations/{id}/handover-photos", reservationId).file(photo)
                        .param("stage", "DELIVERY")
                        .param("latitude", "-12.0500000")
                        .param("longitude", "-77.0500000")
                        .param("note", "Rayón leve en la cabina")
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("DELIVERY"))
                .andExpect(jsonPath("$.imageUrl", startsWith("http://localhost/uploads/")))
                .andExpect(jsonPath("$.latitude").value(-12.05));

        mockMvc.perform(get("/api/v1/reservations/{id}/handover-photos", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].note").value("Rayón leve en la cabina"));
    }

    @Test
    void outsidersCannotSeeHandoverPhotos() throws Exception {
        long reservationId = createReservation(50, 51);
        String outsiderToken = register("209000%05d".formatted(SEQUENCE.getAndIncrement()),
                "outsider" + SEQUENCE.get() + "@checkout.pe").get("accessToken").asText();

        mockMvc.perform(get("/api/v1/reservations/{id}/handover-photos", reservationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsiderToken)))
                .andExpect(status().isForbidden());
    }

    private long createReservation(int startInDays, int endInDays) throws Exception {
        return readId(mockMvc.perform(post("/api/v1/reservations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(renterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentId\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\"}".formatted(equipmentId,
                                LocalDate.now().plusDays(startInDays), LocalDate.now().plusDays(endInDays))))
                .andExpect(status().isCreated()));
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

    private long readId(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
