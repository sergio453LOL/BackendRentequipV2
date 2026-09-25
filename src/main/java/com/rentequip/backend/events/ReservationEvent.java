package com.rentequip.backend.events;

import java.time.Instant;

/**
 * Root of the reservation event hierarchy. Listeners can subscribe to this type to receive every
 * lifecycle change, or to a concrete subclass when they only care about one of them.
 */
public abstract class ReservationEvent {

    private final ReservationSnapshot reservation;
    private final Instant occurredAt;

    protected ReservationEvent(ReservationSnapshot reservation) {
        this.reservation = reservation;
        this.occurredAt = Instant.now();
    }

    public ReservationSnapshot reservation() {
        return reservation;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
