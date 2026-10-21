package com.rentequip.backend.services;

import com.rentequip.backend.dtos.response.EquipmentSummaryResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.EquipmentCategory;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.repositories.EquipmentCategoryRepository;
import com.rentequip.backend.repositories.EquipmentRepository;
import com.rentequip.backend.repositories.HandoverPhotoRepository;
import com.rentequip.backend.repositories.PaymentRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EquipmentSearchTest {

    private static final BigDecimal LIMA_LAT = new BigDecimal("-12.0463731");
    private static final BigDecimal LIMA_LNG = new BigDecimal("-77.0427934");

    @Autowired
    private EquipmentService equipmentService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EquipmentCategoryRepository categoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private HandoverPhotoRepository handoverPhotoRepository;

    private Long excavatorCategoryId;

    @BeforeEach
    void setUp() {
        handoverPhotoRepository.deleteAll();
        paymentRepository.deleteAll();
        reservationRepository.deleteAll();
        equipmentRepository.deleteAll();
        companyRepository.deleteAll();
        categoryRepository.deleteAll();

        EquipmentCategory excavators = categoryRepository.save(category("Excavadoras"));
        EquipmentCategory generators = categoryRepository.save(category("Generadores"));
        excavatorCategoryId = excavators.getId();

        Company owner = companyRepository.save(company("Constructora Andina", "20100000001", "owner@test.pe"));
        equipmentRepository.save(equipment(owner, excavators, "Excavadora cercana",
                new BigDecimal("-12.0563731"), new BigDecimal("-77.0527934"), new BigDecimal("350.00")));
        equipmentRepository.save(equipment(owner, generators, "Generador cercano",
                new BigDecimal("-12.0663731"), new BigDecimal("-77.0627934"), new BigDecimal("120.00")));
        equipmentRepository.save(equipment(owner, excavators, "Excavadora en Arequipa",
                new BigDecimal("-16.4090474"), new BigDecimal("-71.5375284"), new BigDecimal("300.00")));
    }

    @Test
    void returnsOnlyEquipmentInsideTheRadiusOrderedByDistance() {
        PageResponse<EquipmentSummaryResponse> results = search(25.0, null, null);

        assertThat(results.totalElements()).isEqualTo(2);
        assertThat(results.content()).extracting(EquipmentSummaryResponse::name)
                .containsExactly("Excavadora cercana", "Generador cercano");
    }

    @Test
    void widensTheResultSetWhenTheRadiusCoversTheWholeCountry() {
        assertThat(search(900.0, null, null).totalElements()).isEqualTo(3);
    }

    @Test
    void filtersByCategory() {
        PageResponse<EquipmentSummaryResponse> results = search(900.0, excavatorCategoryId, null);

        assertThat(results.totalElements()).isEqualTo(2);
        assertThat(results.content()).extracting(EquipmentSummaryResponse::name)
                .containsExactly("Excavadora cercana", "Excavadora en Arequipa");
    }

    @Test
    void filtersByMaximumDailyRate() {
        PageResponse<EquipmentSummaryResponse> results = search(900.0, null, new BigDecimal("200.00"));

        assertThat(results.content()).extracting(EquipmentSummaryResponse::name)
                .containsExactly("Generador cercano");
    }

    @Test
    void filtersOutEquipmentAlreadyBookedForTheRequestedWindow() {
        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = start.plusDays(2);
        Long nearestId = search(25.0, null, null).content().get(0).id();

        Company renter = companyRepository.save(company("Contratista Uno", "20100000002", "one@test.pe"));
        reservationRepository.save(reservation(nearestId, renter, start, end));

        PageResponse<EquipmentSummaryResponse> results = equipmentService.search(LIMA_LAT, LIMA_LNG, 25.0,
                null, null, null, start, end, null, 0, 20);

        assertThat(results.content()).extracting(EquipmentSummaryResponse::id).doesNotContain(nearestId);
    }

    @Test
    void withoutCoordinatesListsTheWholeCatalogueStartingFromLima() {
        PageResponse<EquipmentSummaryResponse> results = equipmentService.searchCatalog(null, null, null,
                null, null, null, null, null, null, null, 0, 20);

        assertThat(results.content()).extracting(EquipmentSummaryResponse::name)
                .containsExactly("Excavadora cercana", "Generador cercano", "Excavadora en Arequipa");
    }

    @Test
    void withCoordinatesButNoRadiusKeepsTheNearbyDefault() {
        PageResponse<EquipmentSummaryResponse> results = equipmentService.searchCatalog(LIMA_LAT, LIMA_LNG, null,
                null, null, null, null, null, null, null, 0, 20);

        assertThat(results.totalElements()).isEqualTo(2);
    }

    @Test
    void filtersByFreeTextIgnoringCase() {
        PageResponse<EquipmentSummaryResponse> results = equipmentService.searchCatalog(null, null, null,
                "EXCAVADORA", null, null, null, null, null, null, 0, 20);

        assertThat(results.content()).extracting(EquipmentSummaryResponse::name)
                .containsExactly("Excavadora cercana", "Excavadora en Arequipa");
    }

    private PageResponse<EquipmentSummaryResponse> search(double radiusKm, Long categoryId, BigDecimal maxDailyRate) {
        return equipmentService.search(LIMA_LAT, LIMA_LNG, radiusKm, categoryId, null, maxDailyRate,
                null, null, null, 0, 20);
    }

    private com.rentequip.backend.entities.Reservation reservation(Long equipmentId, Company renter,
                                                                   LocalDate start, LocalDate end) {
        com.rentequip.backend.entities.Reservation reservation = new com.rentequip.backend.entities.Reservation();
        reservation.setEquipment(equipmentRepository.findById(equipmentId).orElseThrow());
        reservation.setRenter(renter);
        reservation.setStartDate(start);
        reservation.setEndDate(end);
        reservation.recalculatePricing();
        return reservation;
    }

    private Company company(String name, String taxId, String email) {
        Company company = new Company();
        company.setName(name);
        company.setTaxId(taxId);
        company.setEmail(email);
        company.setCity("Lima");
        company.setLatitude(LIMA_LAT);
        company.setLongitude(LIMA_LNG);
        return company;
    }

    private EquipmentCategory category(String name) {
        EquipmentCategory category = new EquipmentCategory();
        category.setName(name);
        return category;
    }

    private Equipment equipment(Company owner, EquipmentCategory category, String name,
                                BigDecimal latitude, BigDecimal longitude, BigDecimal dailyRate) {
        Equipment equipment = new Equipment();
        equipment.setName(name);
        equipment.setBrand("Caterpillar");
        equipment.setModel("320");
        equipment.setDailyRate(dailyRate);
        equipment.setSecurityDeposit(new BigDecimal("500.00"));
        equipment.setCurrency("PEN");
        equipment.setCity("Lima");
        equipment.setLatitude(latitude);
        equipment.setLongitude(longitude);
        equipment.setOwner(owner);
        equipment.addCategory(category);
        return equipment;
    }
}
