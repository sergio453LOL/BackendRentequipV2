package com.rentequip.backend.events;

import com.rentequip.backend.enums.ReservationStatus;

/**
 * The reservation moved along its lifecycle. Carries the previous status so a listener can tell a
 * confirmation from a cancellation without querying anything.
 */
public class ReservationStatusChangedEvent extends ReservationEvent {

    private final ReservationStatus previousStatus;
    private final String reason;

    public ReservationStatusChangedEvent(ReservationSnapshot reservation,
                                         ReservationStatus previousStatus,
                                         String reason) {
        super(reservation);
        this.previousStatus = previousStatus;
        this.reason = reason;
    }

    public ReservationStatus previousStatus() {
        return previousStatus;
    }

    public ReservationStatus newStatus() {
        return reservation().status();
    }

    public String reason() {
        return reason;
    }
}
