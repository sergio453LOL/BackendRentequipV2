package com.rentequip.backend.dtos.response;

import com.rentequip.backend.enums.ReservationStatus;

import java.time.LocalDate;

/**
 * A period in which a machine cannot be booked. It carries no renter data: any company browsing the
 * catalogue may see when a machine is busy, but not who rented it.
 */
public record BlockedWindowResponse(
        LocalDate startDate,
        LocalDate endDate,
        ReservationStatus status
) {
}
