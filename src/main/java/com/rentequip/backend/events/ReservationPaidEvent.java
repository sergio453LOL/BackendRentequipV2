package com.rentequip.backend.events;

/**
 * A renting company completed the checkout of a reservation. Carries only ids: the listener reads the
 * committed reservation itself, so the receipt shows exactly what was saved.
 *
 * <p>Not a {@link ReservationEvent} on purpose: listeners of the whole hierarchy (the inbox) would
 * otherwise start reacting to payments too.
 *
 * @param reservationId the paid reservation
 * @param payerUserId   the user who paid; the receipt goes to their login email
 */
public record ReservationPaidEvent(Long reservationId, Long payerUserId) {
}
