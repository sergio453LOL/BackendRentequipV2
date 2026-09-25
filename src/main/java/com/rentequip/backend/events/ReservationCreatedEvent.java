package com.rentequip.backend.events;

/**
 * A renting company has requested a machine. The reservation is already holding the calendar in
 * PENDING, so the owner has to answer.
 */
public class ReservationCreatedEvent extends ReservationEvent {

    public ReservationCreatedEvent(ReservationSnapshot reservation) {
        super(reservation);
    }
}
