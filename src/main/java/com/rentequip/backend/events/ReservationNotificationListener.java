package com.rentequip.backend.events;

import com.rentequip.backend.config.AsyncConfig;
import com.rentequip.backend.enums.ReservationStatus;
import com.rentequip.backend.services.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns reservation events into emails, off the request thread.
 *
 * <p>Two annotations carry the whole design. {@code AFTER_COMMIT} means a rolled back booking never
 * produces a message announcing a reservation that does not exist; {@code @Async} means the latency
 * of the mail server is not added to the response time of the endpoint that triggered it.
 */
@Component
@RequiredArgsConstructor
public class ReservationNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationNotificationListener.class);

    private final EmailService emailService;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCreated(ReservationCreatedEvent event) {
        ReservationSnapshot reservation = event.reservation();
        log.info("Reservation {} created, notifying both parties", reservation.reservationId());

        emailService.send(reservation.ownerEmail(),
                "Nueva solicitud de alquiler para " + reservation.equipmentName(),
                "reservation-created-owner", model(reservation));

        emailService.send(reservation.renterEmail(),
                "Hemos recibido tu solicitud de alquiler",
                "reservation-created-renter", model(reservation));
    }

    /**
     * Only the transitions the counterparty needs to hear about produce an email. Internal moves such
     * as IN_PROGRESS are logged and nothing else, so nobody gets a message for every status bump.
     */
    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationStatusChanged(ReservationStatusChangedEvent event) {
        ReservationSnapshot reservation = event.reservation();
        log.info("Reservation {} moved from {} to {}", reservation.reservationId(),
                event.previousStatus(), event.newStatus());

        Map<String, Object> model = model(reservation);
        model.put("reason", event.reason());
        model.put("previousStatus", event.previousStatus());

        switch (event.newStatus()) {
            case CONFIRMED -> emailService.send(reservation.renterEmail(),
                    "Tu reserva de " + reservation.equipmentName() + " fue confirmada",
                    "reservation-confirmed", model);
            case CANCELLED, REJECTED -> notifyBothParties(reservation, model, event.newStatus());
            case COMPLETED -> emailService.send(reservation.renterEmail(),
                    "Alquiler finalizado: cuéntanos cómo te fue",
                    "reservation-completed", model);
            default -> log.debug("No notification configured for status {}", event.newStatus());
        }
    }

    private void notifyBothParties(ReservationSnapshot reservation, Map<String, Object> model,
                                   ReservationStatus status) {
        String subject = status == ReservationStatus.REJECTED
                ? "Tu solicitud de alquiler fue rechazada"
                : "La reserva de " + reservation.equipmentName() + " fue cancelada";
        emailService.send(reservation.renterEmail(), subject, "reservation-cancelled", model);
        emailService.send(reservation.ownerEmail(), subject, "reservation-cancelled", model);
    }

    private Map<String, Object> model(ReservationSnapshot reservation) {
        Map<String, Object> model = new HashMap<>();
        model.put("reservation", reservation);
        return model;
    }
}
