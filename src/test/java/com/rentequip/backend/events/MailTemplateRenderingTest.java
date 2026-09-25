package com.rentequip.backend.events;

import com.rentequip.backend.enums.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders every mail template against a realistic snapshot.
 *
 * <p>Worth its own test because the listener is async and swallows failures on purpose: a broken
 * template would never fail a request, it would just silently stop notifying people.
 */
@SpringBootTest
class MailTemplateRenderingTest {

    @Autowired
    private TemplateEngine templateEngine;

    @ParameterizedTest
    @ValueSource(strings = {
            "reservation-created-owner",
            "reservation-created-renter",
            "reservation-confirmed",
            "reservation-cancelled",
            "reservation-completed"
    })
    void everyTemplateRendersTheReservationDetails(String template) {
        String html = render(template, null);

        assertThat(html).contains("Excavadora CAT 320");
        assertThat(html).contains("Contratista Sur");
        assertThat(html).contains("2375.00");
        assertThat(html).contains("PEN");
        assertThat(html).contains("15/10/2026", "19/10/2026");
        assertThat(html).doesNotContain("th:text");
    }

    @Test
    void theCancellationTemplateQuotesTheReason() {
        String html = render("reservation-cancelled", "La obra se retraso");

        assertThat(html).contains("La obra se retraso");
        assertThat(html).contains("Motivo");
    }

    @Test
    void theReasonBlockDisappearsWhenThereIsNoReason() {
        assertThat(render("reservation-confirmed", null)).doesNotContain("Motivo");
    }

    private String render(String template, String reason) {
        Context context = new Context();
        context.setVariables(Map.of("reservation", snapshot(), "reason", reason == null ? "" : reason));
        return templateEngine.process("mail/" + template, context);
    }

    private ReservationSnapshot snapshot() {
        return new ReservationSnapshot(
                42L, 7L, "Excavadora CAT 320",
                1L, "Constructora Andina", "owner@rentequip.pe",
                2L, "Contratista Sur", "renter@rentequip.pe",
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 19), 5,
                new BigDecimal("2375.00"), "PEN", ReservationStatus.CONFIRMED);
    }
}
