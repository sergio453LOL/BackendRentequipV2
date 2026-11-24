package com.rentequip.backend.config;

import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.entities.Review;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.repositories.EquipmentRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.repositories.ReviewRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Gives every machine of the demo catalogue a few past rentals with their review, so the stars on the
 * cards and the "Opiniones" section of a detail page have something to show. A review needs a completed
 * reservation by another company, so each one comes with its own finished rental by another demo company.
 *
 * <p>It runs after the catalogue seeders, only touches machines of demo companies (e-mail ending in
 * {@code .demo}) that have no review yet, and never uses the companies people register, so their own
 * history stays empty. Everything derives from the machine id, so a fresh database gets the same reviews.
 */
@Component
@ConditionalOnProperty(name = "rentequip.demo-data.enabled", havingValue = "true")
@Order(4)
public class DemoReviewSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoReviewSeeder.class);

    private static final String DEMO_DOMAIN = ".demo";
    private static final int MIN_REVIEWS = 2;
    private static final int MAX_REVIEWS = 5;

    /** Mostly good scores with the odd average one, like a marketplace that works. */
    private static final int[] RATINGS = {5, 4, 5, 5, 4, 3, 5, 4, 5, 4, 5, 3, 4, 5, 5, 4};

    /** Machines that go out with an operator: comments speak of the operator and the work done. */
    private static final Set<String> OPERATED = DemoDataSeeder.OPERATED_CATEGORIES;

    private static final List<String> OPERATED_GOOD = List.of(
            "El operador llegó puntual y conocía bien la máquina. Terminamos el movimiento de tierras un día antes de lo previsto.",
            "Equipo en muy buen estado, sin fugas de aceite. El operador trabajó con cuidado cerca de las redes de agua.",
            "Rindió bien en terreno pedregoso. Coordinamos el traslado con la empresa por WhatsApp sin problemas.",
            "Muy buena experiencia: la máquina llegó limpia, con el mantenimiento al día y el operador con su SCTR vigente.",
            "Cumplieron con la fecha de entrega en obra. El operador nos ayudó a planificar el avance diario.",
            "Buena potencia y consumo de combustible razonable. Volveríamos a alquilarla para la siguiente etapa.",
            "La alquilamos para una obra de saneamiento y respondió muy bien. Atención rápida cuando tuvimos dudas.",
            "Todo según lo acordado: horario, precio y estado del equipo. Recomendado.");

    private static final List<String> TOOL_GOOD = List.of(
            "Herramienta en excelente estado, con su maletín y accesorios completos. La recogimos el mismo día.",
            "Funcionó sin fallas toda la semana. Las baterías duraban la jornada completa.",
            "Muy práctica para trabajos de remodelación. La devolución fue rápida y nos devolvieron la garantía al toque.",
            "Buen equipo y buen trato. Nos explicaron cómo usarlo de forma segura antes de llevarlo.",
            "Potente y bien mantenido. Nos sirvió para abrir pases en losa sin problema.",
            "Precio justo por día y la entrega fue puntual. Lo volveremos a pedir.",
            "Llegó limpio y revisado. Resolvió un trabajo que con herramientas manuales nos hubiera tomado días.",
            "Todo conforme. Coordinación sencilla y la garantía se liberó apenas devolvimos el equipo.");

    private static final List<String> AVERAGE = List.of(
            "Cumplió con el trabajo, aunque llegó con algunas horas de retraso a la obra.",
            "El equipo funcionó, pero se notaba el desgaste. Sería bueno que revisen los accesorios antes de entregarlo.",
            "Buen precio, aunque la coordinación de la entrega pudo ser más clara. El equipo respondió bien.",
            "Trabajo cumplido. Tuvimos que esperar un poco por el cambio de un accesorio, pero lo resolvieron.");

    private final EquipmentRepository equipmentRepository;
    private final CompanyRepository companyRepository;
    private final ReservationRepository reservationRepository;
    private final ReviewRepository reviewRepository;
    private final EntityManager entityManager;

    public DemoReviewSeeder(EquipmentRepository equipmentRepository, CompanyRepository companyRepository,
                            ReservationRepository reservationRepository, ReviewRepository reviewRepository,
                            EntityManager entityManager) {
        this.equipmentRepository = equipmentRepository;
        this.companyRepository = companyRepository;
        this.reservationRepository = reservationRepository;
        this.reviewRepository = reviewRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Company> renters = companyRepository.findAll().stream()
                .filter(DemoReviewSeeder::isDemoCompany)
                .sorted(Comparator.comparing(Company::getId))
                .toList();
        if (renters.size() < 2) {
            return;
        }
        Set<Long> reviewed = reviewRepository.findReviewedEquipmentIds();
        List<Equipment> pending = equipmentRepository.findAll().stream()
                .filter(equipment -> isDemoCompany(equipment.getOwner()))
                .filter(equipment -> !reviewed.contains(equipment.getId()))
                .sorted(Comparator.comparing(Equipment::getId))
                .toList();
        if (pending.isEmpty()) {
            return;
        }

        LocalDateTime startedAt = LocalDateTime.now().minusSeconds(1);
        LocalDate today = LocalDate.now();
        int created = 0;
        for (Equipment equipment : pending) {
            int seed = Math.toIntExact(equipment.getId() % 1_000);
            int count = MIN_REVIEWS + seed % (MAX_REVIEWS - MIN_REVIEWS + 1);
            List<Company> candidates = renters.stream()
                    .filter(company -> !company.getId().equals(equipment.getOwner().getId()))
                    .toList();
            List<String> goodComments = isOperated(equipment) ? OPERATED_GOOD : TOOL_GOOD;
            for (int index = 0; index < count; index++) {
                Company renter = candidates.get((seed * 7 + index * 5) % candidates.size());
                int days = 2 + (seed + index * 3) % 8;
                // Rentals spaced about two and a half months apart, the newest a couple of weeks ago.
                LocalDate endDate = today.minusDays(12L + index * 75L + (seed * 13L) % 50);
                LocalDate startDate = endDate.minusDays(days - 1L);

                Reservation reservation = new Reservation();
                reservation.setEquipment(equipment);
                reservation.setRenter(renter);
                reservation.setStartDate(startDate);
                reservation.setEndDate(endDate);
                reservation.recalculatePricing();
                reservation.setStatus(ReservationStatus.COMPLETED);

                int rating = RATINGS[(seed + index * 3) % RATINGS.length];
                Review review = new Review();
                review.setReservation(reservation);
                review.setRating(rating);
                review.setConditionRating(rating == 5 ? 5 - (seed + index) % 2 : rating);
                review.setComment(rating >= 4
                        ? goodComments.get((seed + index * 3) % goodComments.size())
                        : AVERAGE.get((seed + index) % AVERAGE.size()));
                reservation.setReview(review);

                reservationRepository.save(reservation);
                created++;
            }
        }
        entityManager.flush();
        backdate(startedAt);
        log.info("Demo reviews loaded: {} reviews on {} machines", created, pending.size());
    }

    /**
     * The rows were just written, so they carry today's date. A rental is booked a few days before it
     * starts and reviewed a day or so after it ends; the dates are moved there so the reviews read as past.
     */
    private void backdate(LocalDateTime since) {
        entityManager.createNativeQuery("""
                        UPDATE reservations SET
                            created_at = start_date - (2 + id % 5) * interval '1 day' + (9 + id % 8) * interval '1 hour',
                            updated_at = end_date + interval '1 day' + (10 + id % 7) * interval '1 hour'
                        WHERE created_at >= :since
                        """)
                .setParameter("since", since)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        UPDATE reviews rv SET
                            created_at = r.end_date + (1 + rv.id % 3) * interval '1 day' + (9 + rv.id % 10) * interval '1 hour',
                            updated_at = r.end_date + (1 + rv.id % 3) * interval '1 day' + (9 + rv.id % 10) * interval '1 hour'
                        FROM reservations r
                        WHERE rv.reservation_id = r.id AND rv.created_at >= :since
                        """)
                .setParameter("since", since)
                .executeUpdate();
    }

    private static boolean isDemoCompany(Company company) {
        return company.getEmail() != null && company.getEmail().toLowerCase().endsWith(DEMO_DOMAIN);
    }

    private static boolean isOperated(Equipment equipment) {
        return equipment.getCategories().stream().anyMatch(category -> OPERATED.contains(category.getName()));
    }
}
