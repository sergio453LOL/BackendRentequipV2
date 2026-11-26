package com.rentequip.backend.events;

import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.events.ReservationCalendar.CalendarInvite;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calendar event a confirmed reservation becomes: all the rented days, matched by UID between the
 * invitation and its cancellation, and valid iCalendar text (escaped, folded, CRLF).
 */
class ReservationCalendarTest {

    private final ReservationSnapshot reservation = new ReservationSnapshot(
            42L, 7L, "Excavadora CAT 320; brazo largo", "Av. Argentina 1234, Lima",
            1L, "Constructora Andina", "owner@rentequip.pe",
            2L, "Contratista Sur", "renter@rentequip.pe",
            LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 19), 5,
            new BigDecimal("2375"), "PEN", ReservationStatus.CONFIRMED);

    @Test
    void theInvitationCoversEveryRentedDayAndInvitesTheRenter() {
        CalendarInvite invite = ReservationCalendar.invite(reservation, "no-reply@rentequip.pe");
        String ics = unfold(invite.content());

        assertThat(invite.method()).isEqualTo("REQUEST");
        assertThat(ics).contains("METHOD:REQUEST", "UID:reserva-42@rentequip.pe", "STATUS:CONFIRMED",
                "DTSTART;VALUE=DATE:20261015",
                // The end is exclusive: the 19th is still a rented day.
                "DTEND;VALUE=DATE:20261020",
                "SUMMARY:Alquiler: Excavadora CAT 320\\; brazo largo (RentEquip)",
                "LOCATION:Av. Argentina 1234\\, Lima",
                "ORGANIZER;CN=\"Constructora Andina\":mailto:no-reply@rentequip.pe",
                "mailto:renter@rentequip.pe",
                "PEN 2375.00");
    }

    @Test
    void theCancellationRemovesTheSameEvent() {
        String ics = unfold(ReservationCalendar.cancellation(reservation, "no-reply@rentequip.pe").content());

        assertThat(ics).contains("METHOD:CANCEL", "UID:reserva-42@rentequip.pe", "STATUS:CANCELLED", "SEQUENCE:1");
    }

    @Test
    void theTextIsValidICalendar() {
        String content = ReservationCalendar.invite(reservation, "no-reply@rentequip.pe").content();

        assertThat(content).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(content.replace("\r\n", "")).doesNotContain("\n");
        assertThat(Arrays.stream(content.split("\r\n")))
                .allSatisfy(line -> assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75));
    }

    @Test
    void theGoogleLinkOpensTheEventFilledIn() {
        String url = ReservationCalendar.googleCalendarUrl(reservation);

        assertThat(url).startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE")
                .contains("&dates=20261015/20261020");
        assertThat(URLDecoder.decode(url, StandardCharsets.UTF_8))
                .contains("text=Alquiler: Excavadora CAT 320; brazo largo (RentEquip)")
                .contains("location=Av. Argentina 1234, Lima");
    }

    /** Undoes the RFC 5545 line folding so assertions read whole properties. */
    private static String unfold(String content) {
        return content.replace("\r\n ", "");
    }
}
