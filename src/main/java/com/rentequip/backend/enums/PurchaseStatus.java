package com.rentequip.backend.enums;

/**
 * Life of a renter's request to buy the machine they rented: the owner accepts or rejects it, and an
 * accepted request is paid by card in the app.
 */
public enum PurchaseStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    PAID
}
