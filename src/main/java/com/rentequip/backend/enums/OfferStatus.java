package com.rentequip.backend.enums;

/**
 * Life of a price negotiation. The renter proposes (PENDING); the owner accepts, rejects or counters
 * (COUNTERED); the renter then accepts or rejects the counter. An ACCEPTED offer is spent (USED) by the
 * reservation that books at the agreed rate.
 */
public enum OfferStatus {
    PENDING,
    COUNTERED,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    USED
}
