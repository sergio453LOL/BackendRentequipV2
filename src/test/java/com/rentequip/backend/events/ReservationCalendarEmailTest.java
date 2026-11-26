package com.rentequip.backend.events;

import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.events.ReservationCalendar.CalendarInvite;
import com.rentequip.backend.services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which emails carry the calendar event: the confirmation adds it to the renter's calendar, cancelling a
 * confirmed reservation removes it, and a request rejected before it was ever confirmed sends nothing to remove.
 */
class ReservationCalendarEmailTest {

    private final EmailService emailService = mock(EmailService.class);
    private final ReservationNotificationListener listener = new ReservationNotificationListener(emailService);

    @BeforeEach
    void organizer() {
        when(emailService.fromAddress()).thenReturn("no-reply@rentequip.pe");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aConfirmationAddsTheReservationToTheRentersCalendar() {
        listener.onReservationStatusChanged(changed(ReservationStatus.PENDING, ReservationStatus.CONFIRMED));

        ArgumentCaptor<Map<String, Object>> model = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<CalendarInvite> invite = ArgumentCaptor.forClass(CalendarInvite.class);
        verify(emailService).send(eq("renter@rentequip.pe"), anyString(), eq("reservation-confirmed"),
                model.capture(), invite.capture());
        assertThat(invite.getValue().method()).isEqualTo("REQUEST");
        assertThat((String) model.getValue().get("calendarUrl")).startsWith("https://calendar.google.com/");
    }

    @Test
    void cancellingAConfirmedReservationTakesItOffTheCalendar() {
        listener.onReservationStatusChanged(changed(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED));

        ArgumentCaptor<CalendarInvite> invite = ArgumentCaptor.forClass(CalendarInvite.class);
        verify(emailService).send(eq("renter@rentequip.pe"), anyString(), eq("reservation-cancelled"),
                anyMap(), invite.capture());
        assertThat(invite.getValue().method()).isEqualTo("CANCEL");
        verify(emailService).send(eq("owner@rentequip.pe"), anyString(), eq("reservation-cancelled"), anyMap());
    }

    @Test
    void aRejectedRequestWasNeverOnTheCalendar() {
        listener.onReservationStatusChanged(changed(ReservationStatus.PENDING, ReservationStatus.REJECTED));

        verify(emailService).send(eq("renter@rentequip.pe"), anyString(), eq("reservation-cancelled"),
                anyMap(), isNull());
    }

    private static ReservationStatusChangedEvent changed(ReservationStatus from, ReservationStatus to) {
        ReservationSnapshot reservation = new ReservationSnapshot(
                42L, 7L, "Excavadora CAT 320", "Av. Argentina 1234, Lima",
                1L, "Constructora Andina", "owner@rentequip.pe",
                2L, "Contratista Sur", "renter@rentequip.pe",
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 19), 5,
                new BigDecimal("2375.00"), "PEN", to);
        return new ReservationStatusChangedEvent(reservation, from, null);
    }
}
