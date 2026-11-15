package com.rentequip.backend.enums;

public enum EquipmentStatus {
    AVAILABLE,
    RENTED,
    MAINTENANCE,
    INACTIVE,
    /** Bought by a renter through a purchase request; it leaves the catalogue for good. */
    SOLD
}
