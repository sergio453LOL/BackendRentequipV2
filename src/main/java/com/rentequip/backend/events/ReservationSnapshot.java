package com.rentequip.backend.events;

import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable copy of everything a notification needs, read while the transaction is still open.
 *
 * <p>Events are consumed after the commit and on another thread, where the persistence context is
 * already gone: handing over the entity would blow up with a LazyInitializationException the first
 * time the listener touched a relation.
 */
public record ReservationSnapshot(
        Long reservationId,
        Long equipmentId,
        String equipmentName,
        /** Where the machine is picked up (its own address, else the owner's); used as the calendar location. */
        String equipmentAddress,
        Long ownerCompanyId,
        String ownerCompanyName,
        String ownerEmail,
        Long renterCompanyId,
        String renterCompanyName,
        String renterEmail,
        LocalDate startDate,
        LocalDate endDate,
        Integer totalDays,
        BigDecimal totalAmount,
        String currency,
        ReservationStatus status
) {

    public static ReservationSnapshot of(Reservation reservation) {
        return new ReservationSnapshot(
                reservation.getId(),
                reservation.getEquipment().getId(),
                reservation.getEquipment().getName(),
                firstNonBlank(reservation.getEquipment().getAddress(), reservation.getEquipment().getOwner().getAddress()),
                reservation.getEquipment().getOwner().getId(),
                reservation.getEquipment().getOwner().getName(),
                reservation.getEquipment().getOwner().getEmail(),
                reservation.getRenter().getId(),
                reservation.getRenter().getName(),
                reservation.getRenter().getEmail(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getTotalDays(),
                reservation.getTotalAmount(),
                reservation.getCurrency(),
                reservation.getStatus()
        );
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
