package com.rentequip.backend.events;

import com.rentequip.backend.enums.NotificationType;
import com.rentequip.backend.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Turns reservation events into in-app notifications. Unlike the emails it runs inside the publishing
 * transaction, so the alert and the booking change are committed (or rolled back) together.
 */
@Component
@RequiredArgsConstructor
public class ReservationInboxListener {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final String OWNER_LINK = "/reservations?role=OWNER";
    private static final String RENTER_LINK = "/reservations?role=RENTER";

    private final NotificationService notificationService;

    @EventListener
    public void onCreated(ReservationCreatedEvent event) {
        ReservationSnapshot r = event.reservation();
        notificationService.notify(r.ownerCompanyId(), NotificationType.RESERVATION_REQUESTED,
                "Nueva solicitud: " + r.equipmentName(),
                r.renterCompanyName() + " quiere alquilarla " + dates(r) + ". Acéptala o recházala.",
                OWNER_LINK);
    }

    @EventListener
    public void onStatusChanged(ReservationStatusChangedEvent event) {
        ReservationSnapshot r = event.reservation();
        switch (event.newStatus()) {
            case CONFIRMED -> notificationService.notify(r.renterCompanyId(), NotificationType.RESERVATION_CONFIRMED,
                    "Reserva confirmada: " + r.equipmentName(),
                    r.ownerCompanyName() + " aceptó tu solicitud " + dates(r) + ".", RENTER_LINK);
            case REJECTED -> notificationService.notify(r.renterCompanyId(), NotificationType.RESERVATION_REJECTED,
                    "Solicitud rechazada: " + r.equipmentName(),
                    r.ownerCompanyName() + " no puede atenderla" + reasonOf(event) + ".", RENTER_LINK);
            case CANCELLED -> {
                // The side that did not cancel is the one to warn; without knowing who did, both hear it.
                notificationService.notify(r.ownerCompanyId(), NotificationType.RESERVATION_CANCELLED,
                        "Reserva cancelada: " + r.equipmentName(),
                        "La reserva de " + r.renterCompanyName() + " " + dates(r) + " se canceló" + reasonOf(event) + ".",
                        OWNER_LINK);
                notificationService.notify(r.renterCompanyId(), NotificationType.RESERVATION_CANCELLED,
                        "Reserva cancelada: " + r.equipmentName(),
                        "Tu reserva " + dates(r) + " se canceló" + reasonOf(event) + ".", RENTER_LINK);
            }
            case IN_PROGRESS -> notificationService.notify(r.renterCompanyId(), NotificationType.RESERVATION_STARTED,
                    "Alquiler en curso: " + r.equipmentName(),
                    "La máquina ya está entregada. Devuélvela el " + r.endDate().format(DAY) + ".", RENTER_LINK);
            case COMPLETED -> notificationService.notify(r.renterCompanyId(), NotificationType.RESERVATION_COMPLETED,
                    "Alquiler terminado: " + r.equipmentName(),
                    "Gracias por alquilar con " + r.ownerCompanyName() + ". Cuéntanos qué tal te fue con una reseña.",
                    RENTER_LINK);
            default -> {
                // PENDING is only the starting state: nothing to announce.
            }
        }
    }

    private static String dates(ReservationSnapshot r) {
        return "del " + r.startDate().format(DAY) + " al " + r.endDate().format(DAY);
    }

    private static String reasonOf(ReservationStatusChangedEvent event) {
        return event.reason() == null || event.reason().isBlank() ? "" : " (" + event.reason().trim() + ")";
    }
}
