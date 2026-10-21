package com.rentequip.backend.enums;

/**
 * Payment situation of a reservation as a whole, derived from its individual payments.
 */
public enum ReservationPaymentStatus {
    UNPAID,
    PAID,
    REFUNDED
}
