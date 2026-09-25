package com.rentequip.backend.events;

import com.rentequip.backend.dtos.request.ReservationCreateRequest;
import com.rentequip.backend.dtos.request.ReservationStatusUpdateRequest;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.enums.UserRole;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.repositories.EquipmentRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.security.CompanyUserDetails;
import com.rentequip.backend.services.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the booking flow actually publishes its domain events and that the snapshot they
 * carry is complete, which is what lets the listener build an email without touching the database.
 */
@SpringBootTest
@RecordApplicationEvents
class ReservationEventsTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ApplicationEvents events;

    private Long equipmentId;
    private Long ownerId;
    private Long renterId;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        equipmentRepository.deleteAll();
        companyRepository.deleteAll();

        Company owner = companyRepository.save(newCompany("Constructora Andina", "20300000001", "owner@events.pe"));
        Company renter = companyRepository.save(newCompany("Contratista Sur", "20300000002", "renter@events.pe"));
        Equipment equipment = equipmentRepository.save(newEquipment(owner));

        ownerId = owner.getId();
        renterId = renter.getId();
        equipmentId = equipment.getId();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void creatingAReservationPublishesACreatedEventWithBothEmails() {
        authenticateAs(renterId);
        LocalDate start = LocalDate.now().plusDays(5);

        reservationService.create(new ReservationCreateRequest(equipmentId, start, start.plusDays(3), null));

        List<ReservationCreatedEvent> published = events.stream(ReservationCreatedEvent.class).toList();
        assertThat(published).hasSize(1);

        ReservationSnapshot snapshot = published.getFirst().reservation();
        assertThat(snapshot.ownerEmail()).isEqualTo("owner@events.pe");
        assertThat(snapshot.renterEmail()).isEqualTo("renter@events.pe");
        assertThat(snapshot.equipmentName()).isEqualTo("Excavadora CAT 320");
        assertThat(snapshot.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(snapshot.totalDays()).isEqualTo(4);
        assertThat(snapshot.totalAmount()).isNotNull();
    }

    @Test
    void confirmingAReservationPublishesAStatusChangeCarryingThePreviousStatus() {
        authenticateAs(renterId);
        LocalDate start = LocalDate.now().plusDays(5);
        Long reservationId = reservationService
                .create(new ReservationCreateRequest(equipmentId, start, start.plusDays(3), null)).id();

        authenticateAs(ownerId);
        reservationService.confirm(reservationId);

        List<ReservationStatusChangedEvent> published =
                events.stream(ReservationStatusChangedEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.getFirst().previousStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(published.getFirst().newStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void cancellingAReservationPublishesTheReasonSoItCanBeQuotedInTheEmail() {
        authenticateAs(renterId);
        LocalDate start = LocalDate.now().plusDays(5);
        Long reservationId = reservationService
                .create(new ReservationCreateRequest(equipmentId, start, start.plusDays(3), null)).id();

        reservationService.updateStatus(reservationId,
                new ReservationStatusUpdateRequest(ReservationStatus.CANCELLED, "La obra se retrasó"));

        ReservationStatusChangedEvent event =
                events.stream(ReservationStatusChangedEvent.class).toList().getFirst();
        assertThat(event.newStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(event.reason()).isEqualTo("La obra se retrasó");
        assertThat(event.occurredAt()).isNotNull();
    }

    private void authenticateAs(Long companyId) {
        CompanyUserDetails principal = new CompanyUserDetails(
                companyId, "user%d@events.pe".formatted(companyId), null, companyId, UserRole.ADMIN, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Company newCompany(String name, String taxId, String email) {
        Company company = new Company();
        company.setName(name);
        company.setTaxId(taxId);
        company.setEmail(email);
        company.setCity("Lima");
        company.setLatitude(new BigDecimal("-12.0463731"));
        company.setLongitude(new BigDecimal("-77.0427934"));
        return company;
    }

    private Equipment newEquipment(Company owner) {
        Equipment equipment = new Equipment();
        equipment.setName("Excavadora CAT 320");
        equipment.setBrand("Caterpillar");
        equipment.setModel("320");
        equipment.setDailyRate(new BigDecimal("350.00"));
        equipment.setSecurityDeposit(new BigDecimal("500.00"));
        equipment.setCurrency("PEN");
        equipment.setCity("Lima");
        equipment.setLatitude(new BigDecimal("-12.0563731"));
        equipment.setLongitude(new BigDecimal("-77.0527934"));
        equipment.setOwner(owner);
        return equipment;
    }
}
