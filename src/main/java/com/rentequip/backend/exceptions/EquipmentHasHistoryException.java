package com.rentequip.backend.exceptions;

import org.springframework.http.HttpStatus;

/**
 * A machine with reservations, offers or purchase requests behind it cannot be removed: both companies keep
 * that history (payments, receipts, reviews). The owner takes it off the market by setting it INACTIVE.
 */
public class EquipmentHasHistoryException extends RentEquipException {

    public EquipmentHasHistoryException(Long equipmentId) {
        super("Equipment %d has reservations, offers or purchases in its history and cannot be deleted; set it to INACTIVE instead"
                .formatted(equipmentId), HttpStatus.CONFLICT, "EQUIPMENT_HAS_HISTORY");
    }
}
