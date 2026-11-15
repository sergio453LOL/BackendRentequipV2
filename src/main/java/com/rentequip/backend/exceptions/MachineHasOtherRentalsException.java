package com.rentequip.backend.exceptions;

import org.springframework.http.HttpStatus;

/** A machine cannot be sold while other renters still hold it with pending, confirmed or active reservations. */
public class MachineHasOtherRentalsException extends RentEquipException {

    public MachineHasOtherRentalsException(Long equipmentId) {
        super("Equipment " + equipmentId + " has other pending or active rentals; it can be sold when they end",
                HttpStatus.CONFLICT, "MACHINE_HAS_OTHER_RENTALS");
    }
}
