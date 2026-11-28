package com.rentequip.backend.services.assistant;

import com.rentequip.backend.BackendApplication;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.EquipmentCategory;
import com.rentequip.backend.entities.Payment;
import com.rentequip.backend.entities.PriceOffer;
import com.rentequip.backend.entities.PurchaseRequest;
import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.entities.Review;
import com.rentequip.backend.entities.User;
import com.rentequip.backend.enums.EquipmentStatus;
import com.rentequip.backend.enums.PurchaseStatus;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.repositories.EquipmentRepository;
import com.rentequip.backend.repositories.NewsletterSubscriptionRepository;
import com.rentequip.backend.repositories.PaymentRepository;
import com.rentequip.backend.repositories.PriceOfferRepository;
import com.rentequip.backend.repositories.PurchaseRequestRepository;
import com.rentequip.backend.repositories.ReservationRepository;
import com.rentequip.backend.repositories.ReviewRepository;
import com.rentequip.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The platform in figures, written as plain text for the admin copilot's model. Everything comes from the
 * database at the moment of the question; it holds company names and amounts, never e-mails, phones or tax
 * ids, so the model cannot repeat contact data it does not need. Lists are cut to their first entries to
 * keep the prompt small.
 */
@Component
@RequiredArgsConstructor
public class PlatformSnapshot {

    private static final ZoneId MARKET = ZoneId.of(BackendApplication.MARKET_TIME_ZONE);
    private static final int TOP = 8;
    /** Reservations that moved money or will: what the panel calls the volume of business. */
    private static final Set<ReservationStatus> BOOKED = EnumSet.of(ReservationStatus.CONFIRMED,
            ReservationStatus.IN_PROGRESS, ReservationStatus.COMPLETED);

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final PriceOfferRepository offerRepository;
    private final PurchaseRequestRepository purchaseRepository;
    private final ReviewRepository reviewRepository;
    private final NewsletterSubscriptionRepository newsletterRepository;

    /** The figures and the short summary shown when no model can answer. */
    public record Facts(String text, String summary) {
    }

    @Transactional(readOnly = true)
    public Facts capture() {
        LocalDate today = LocalDate.now(MARKET);
        LocalDateTime monthAgo = today.minusDays(30).atStartOfDay();
        LocalDateTime weekAgo = today.minusDays(7).atStartOfDay();

        List<Company> companies = companyRepository.findAll();
        List<User> users = userRepository.findAll();
        List<Equipment> equipment = equipmentRepository.findAll();
        List<Reservation> reservations = reservationRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();
        List<PriceOffer> offers = offerRepository.findAll();
        List<PurchaseRequest> purchases = purchaseRepository.findAll();
        List<Review> reviews = reviewRepository.findAll();
        long subscribers = newsletterRepository.count();

        StringBuilder out = new StringBuilder();
        out.append("Fecha de hoy (Lima): ").append(today).append('\n');

        // Companies and the KYB queue.
        long active = companies.stream().filter(Company::isActive).count();
        long verified = companies.stream().filter(Company::isVerified).count();
        List<Company> pending = companies.stream()
                .filter(company -> company.isActive() && !company.isVerified())
                .sorted(Comparator.comparing(Company::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        section(out, "Empresas");
        line(out, "Total", companies.size());
        line(out, "Activas", active);
        line(out, "Verificadas (KYB aprobado)", verified);
        line(out, "Esperando verificación", pending.size());
        line(out, "Registradas en los últimos 7 días", companies.stream().filter(c -> after(c.getCreatedAt(), weekAgo)).count());
        line(out, "Registradas en los últimos 30 días", companies.stream().filter(c -> after(c.getCreatedAt(), monthAgo)).count());
        line(out, "Empresas por ciudad", top(countBy(companies, Company::getCity)));
        if (!pending.isEmpty()) {
            line(out, "Más antiguas esperando verificación", pending.stream().limit(TOP)
                    .map(company -> "%s (%s, desde %s)".formatted(company.getName(), orDash(company.getCity()),
                            company.getCreatedAt() == null ? "-" : company.getCreatedAt().toLocalDate()))
                    .collect(Collectors.joining("; ")));
        }

        section(out, "Usuarios");
        line(out, "Total", users.size());
        line(out, "Habilitados", users.stream().filter(User::isEnabled).count());
        line(out, "Nuevos en los últimos 30 días", users.stream().filter(u -> after(u.getCreatedAt(), monthAgo)).count());
        line(out, "Suscriptores del boletín", subscribers);

        // Catalogue.
        List<Equipment> listed = equipment.stream().filter(item -> item.getStatus() != EquipmentStatus.SOLD
                && item.getStatus() != EquipmentStatus.INACTIVE).toList();
        section(out, "Catálogo");
        line(out, "Equipos publicados (sin contar vendidos ni inactivos)", listed.size());
        line(out, "Por estado", countBy(equipment, item -> item.getStatus().name()));
        line(out, "Equipos por ciudad", top(countBy(listed, Equipment::getCity)));
        line(out, "Equipos por categoría", top(countBy(listed.stream()
                .flatMap(item -> item.getCategories().stream()).toList(), EquipmentCategory::getName)));
        line(out, "Con operador incluido", listed.stream().filter(Equipment::isOperatorIncluded).count());
        line(out, "A la venta", listed.stream().filter(item -> item.getSalePrice() != null).count());
        line(out, "Sin fotos", listed.stream().filter(item -> item.getImageUrls().isEmpty()).count());
        line(out, "Con video", listed.stream().filter(item -> item.getVideoUrl() != null && !item.getVideoUrl().isBlank()).count());
        line(out, "Tarifa diaria media por moneda", averageBy(listed, Equipment::getCurrency, Equipment::getDailyRate));
        line(out, "Empresas con más equipos publicados", top(countBy(listed, item -> item.getOwner().getName())));

        // Reservations: the business.
        List<Reservation> booked = reservations.stream().filter(r -> BOOKED.contains(r.getStatus())).toList();
        section(out, "Reservas");
        line(out, "Total", reservations.size());
        line(out, "Por estado", countBy(reservations, r -> r.getStatus().name()));
        line(out, "Creadas en los últimos 30 días", reservations.stream().filter(r -> after(r.getCreatedAt(), monthAgo)).count());
        line(out, "Volumen reservado (confirmadas, en curso y completadas)", sumBy(booked, Reservation::getCurrency, Reservation::getTotalAmount));
        line(out, "Volumen reservado en los últimos 30 días", sumBy(booked.stream()
                .filter(r -> after(r.getCreatedAt(), monthAgo)).toList(), Reservation::getCurrency, Reservation::getTotalAmount));
        line(out, "Duración media en días", booked.isEmpty() ? "-" : BigDecimal.valueOf(booked.stream()
                .map(Reservation::getTotalDays).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0))
                .setScale(1, RoundingMode.HALF_UP));
        line(out, "En curso hoy", reservations.stream().filter(r -> r.getStatus() == ReservationStatus.IN_PROGRESS).count());
        line(out, "Equipos más reservados", top(countBy(booked, r -> r.getEquipment().getName() + " (" + orDash(r.getEquipment().getCity()) + ")")));
        line(out, "Ciudades con más reservas", top(countBy(booked, r -> r.getEquipment().getCity())));
        line(out, "Empresas que más alquilan", top(countBy(booked, r -> r.getRenter().getName())));
        line(out, "Empresas que más facturan como dueñas", topAmounts(booked, r -> r.getEquipment().getOwner().getName(), Reservation::getTotalAmount));

        section(out, "Pagos");
        line(out, "Por estado", countBy(payments, p -> p.getStatus().name()));
        line(out, "Importe por estado", payments.stream().collect(Collectors.groupingBy(p -> p.getStatus().name(), TreeMap::new,
                Collectors.collectingAndThen(Collectors.toList(), list -> sumBy(list, Payment::getCurrency, Payment::getAmount)))));
        line(out, "Motivos de pagos fallidos", top(countBy(payments.stream().filter(p -> p.getFailureReason() != null).toList(),
                Payment::getFailureReason)));

        section(out, "Ofertas de precio y compras");
        line(out, "Ofertas por estado", countBy(offers, o -> o.getStatus().name()));
        line(out, "Solicitudes de compra por estado", countBy(purchases, p -> p.getStatus().name()));
        line(out, "Ventas pagadas", sumBy(purchases.stream().filter(p -> p.getStatus() == PurchaseStatus.PAID).toList(),
                PurchaseRequest::getCurrency, PurchaseRequest::getFinalPrice));

        section(out, "Reseñas");
        line(out, "Total", reviews.size());
        line(out, "Nota media (1 a 5)", reviews.isEmpty() ? "-" : BigDecimal.valueOf(reviews.stream()
                .map(Review::getRating).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0))
                .setScale(2, RoundingMode.HALF_UP));
        line(out, "Reseñas de 1 o 2 estrellas", reviews.stream().filter(r -> r.getRating() != null && r.getRating() <= 2).count());

        String summary = """
                Resumen de la plataforma al %s:
                - Empresas: %d (%d verificadas, %d esperando verificación).
                - Equipos publicados: %d.
                - Reservas: %d, de las cuales %d confirmadas, en curso o completadas.
                - Volumen reservado: %s.
                - Reseñas: %d.""".formatted(today, companies.size(), verified, pending.size(), listed.size(),
                reservations.size(), booked.size(), sumBy(booked, Reservation::getCurrency, Reservation::getTotalAmount),
                reviews.size());
        return new Facts(out.toString(), summary);
    }

    private static void section(StringBuilder out, String title) {
        out.append('\n').append("## ").append(title).append('\n');
    }

    private static void line(StringBuilder out, String label, Object value) {
        String text = value instanceof Map<?, ?> map && map.isEmpty() ? "-" : String.valueOf(value);
        out.append("- ").append(label).append(": ").append(text).append('\n');
    }

    private static boolean after(LocalDateTime moment, LocalDateTime threshold) {
        return moment != null && !moment.isBefore(threshold);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /** "S/ 71,450.00", "US$ 1,200.00": the way the panel writes money, so the model repeats it as is. */
    private static String money(String currency, BigDecimal amount) {
        String symbol = switch (currency) {
            case "PEN" -> "S/";
            case "USD" -> "US$";
            default -> currency;
        };
        return symbol + " " + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US))
                .format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    private static <T> Map<String, Long> countBy(Collection<T> items, Function<T, String> key) {
        return items.stream().collect(Collectors.groupingBy(item -> orDash(key.apply(item)), TreeMap::new, Collectors.counting()));
    }

    /** The largest groups first, cut to the first entries. */
    private static String top(Map<String, Long> counts) {
        if (counts.isEmpty()) {
            return "-";
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP)
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private static <T> String sumBy(Collection<T> items, Function<T, String> currency, Function<T, BigDecimal> amount) {
        Map<String, BigDecimal> sums = new TreeMap<>();
        for (T item : items) {
            BigDecimal value = amount.apply(item);
            if (value != null) {
                sums.merge(orDash(currency.apply(item)), value, BigDecimal::add);
            }
        }
        if (sums.isEmpty()) {
            return "0";
        }
        return sums.entrySet().stream()
                .map(entry -> money(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static <T> String averageBy(Collection<T> items, Function<T, String> currency, Function<T, BigDecimal> amount) {
        Map<String, List<BigDecimal>> groups = items.stream().filter(item -> amount.apply(item) != null)
                .collect(Collectors.groupingBy(item -> orDash(currency.apply(item)), TreeMap::new,
                        Collectors.mapping(amount, Collectors.toList())));
        if (groups.isEmpty()) {
            return "-";
        }
        return groups.entrySet().stream()
                .map(entry -> money(entry.getKey(), entry.getValue().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(entry.getValue().size()), 2, RoundingMode.HALF_UP)))
                .collect(Collectors.joining(", "));
    }

    /** Groups with the largest amounts, all currencies added as soles (the catalogue is almost all PEN). */
    private static <T> String topAmounts(Collection<T> items, Function<T, String> key, Function<T, BigDecimal> amount) {
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        for (T item : items) {
            BigDecimal value = amount.apply(item);
            if (value != null) {
                sums.merge(orDash(key.apply(item)), value, BigDecimal::add);
            }
        }
        if (sums.isEmpty()) {
            return "-";
        }
        return sums.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(TOP)
                .map(entry -> entry.getKey() + ": " + money("PEN", entry.getValue()))
                .collect(Collectors.joining(", "));
    }
}
