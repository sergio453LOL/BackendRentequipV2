package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.ReservationCreateRequest;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.EquipmentCategory;
import com.rentequip.backend.enums.UserRole;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.OverbookingException;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.repositories.EquipmentCategoryRepository;
import com.rentequip.backend.repositories.EquipmentRepository;
import com.rentequip.backend.repositories.HandoverPhotoRepository;
import com.rentequip.backend.repositories.PaymentRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.security.CompanyUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ReservationConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

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

    private Long equipmentId;
    private Long ownerId;
    private Long firstRenterId;
    private Long secondRenterId;

    @BeforeEach
    void setUp() {
        handoverPhotoRepository.deleteAll();
        paymentRepository.deleteAll();
        reservationRepository.deleteAll();
        equipmentRepository.deleteAll();
        companyRepository.deleteAll();
        categoryRepository.deleteAll();

        EquipmentCategory category = categoryRepository.save(newCategory());
        Company owner = companyRepository.save(newCompany("Constructora Andina", "20100000001", "owner@test.pe"));
        Company firstRenter = companyRepository.save(newCompany("Contratista Uno", "20100000002", "one@test.pe"));
        Company secondRenter = companyRepository.save(newCompany("Contratista Dos", "20100000003", "two@test.pe"));
        Equipment equipment = equipmentRepository.save(newEquipment(owner, category));

        ownerId = owner.getId();
        firstRenterId = firstRenter.getId();
        secondRenterId = secondRenter.getId();
        equipmentId = equipment.getId();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsTheSecondReservationWhenDatesOverlap() {
        LocalDate start = LocalDate.now().plusDays(3);
        LocalDate end = start.plusDays(4);

        authenticateAs(firstRenterId);
        reservationService.create(request(start, end));

        authenticateAs(secondRenterId);
        assertThatThrownBy(() -> reservationService.create(request(start.plusDays(2), end.plusDays(2))))
                .isInstanceOf(OverbookingException.class);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void acceptsBackToBackReservationsThatDoNotOverlap() {
        LocalDate start = LocalDate.now().plusDays(3);

        authenticateAs(firstRenterId);
        reservationService.create(request(start, start.plusDays(2)));

        authenticateAs(secondRenterId);
        reservationService.create(request(start.plusDays(3), start.plusDays(5)));

        assertThat(reservationRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsAReservationOnTheCompanyOwnEquipment() {
        LocalDate start = LocalDate.now().plusDays(3);

        authenticateAs(ownerId);
        assertThatThrownBy(() -> reservationService.create(request(start, start.plusDays(1))))
                .isInstanceOf(InvalidOperationException.class);
    }

    /**
     * Ten companies race for the same window, each on its own authenticated thread. The
     * PESSIMISTIC_WRITE lock on the equipment row must let exactly one through and turn the other nine
     * into 409 responses.
     */
    @Test
    void onlyOneOfTenSimultaneousRequestsWins() throws Exception {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = start.plusDays(3);
        int attempts = 10;

        List<Company> renters = seedRenters(attempts);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            List<Future<Void>> futures = renters.stream()
                    .map(renter -> pool.submit(bookingAttempt(startGate, renter.getId(), start, end,
                            succeeded, rejected)))
                    .toList();
            startGate.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(attempts - 1);
        assertThat(reservationRepository.findOverlapping(equipmentId, start, end)).hasSize(1);
    }

    private Callable<Void> bookingAttempt(CountDownLatch startGate, Long renterId, LocalDate start, LocalDate end,
                                          AtomicInteger succeeded, AtomicInteger rejected) {
        return () -> {
            startGate.await();
            try {
                // The SecurityContext is thread local, so every racing thread authenticates on its own.
                authenticateAs(renterId);
                reservationService.create(request(start, end));
                succeeded.incrementAndGet();
            } catch (RuntimeException expected) {
                rejected.incrementAndGet();
            } finally {
                SecurityContextHolder.clearContext();
            }
            return null;
        };
    }

    /**
     * Puts a principal for the given company in the SecurityContext, which is where the service now
     * reads the renting company from.
     */
    private void authenticateAs(Long companyId) {
        CompanyUserDetails principal = new CompanyUserDetails(
                companyId, "company%d@test.pe".formatted(companyId), null, companyId, UserRole.ADMIN, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private List<Company> seedRenters(int amount) {
        return java.util.stream.IntStream.range(0, amount)
                .mapToObj(index -> companyRepository.save(newCompany(
                        "Contratista " + index,
                        "209%08d".formatted(index),
                        "renter%d@test.pe".formatted(index))))
                .toList();
    }

    private ReservationCreateRequest request(LocalDate start, LocalDate end) {
        return new ReservationCreateRequest(equipmentId, start, end, null);
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

    private EquipmentCategory newCategory() {
        EquipmentCategory category = new EquipmentCategory();
        category.setName("Excavadoras");
        category.setDescription("Maquinaria de movimiento de tierras");
        return category;
    }

    private Equipment newEquipment(Company owner, EquipmentCategory category) {
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
        equipment.addCategory(category);
        return equipment;
    }
}
