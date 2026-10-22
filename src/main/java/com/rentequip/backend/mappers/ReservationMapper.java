package com.rentequip.backend.mappers;

import com.rentequip.backend.dtos.request.ReservationCreateRequest;
import com.rentequip.backend.dtos.response.ReservationResponse;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.Reservation;
import com.rentequip.backend.services.PaymentService;
import com.rentequip.backend.enums.ReservationStatus;
import org.springframework.stereotype.Component;

@Component
public class ReservationMapper {

    public Reservation toEntity(ReservationCreateRequest request, Equipment equipment, Company renter) {
        Reservation reservation = new Reservation();
        reservation.setEquipment(equipment);
        reservation.setRenter(renter);
        reservation.setStartDate(request.startDate());
        reservation.setEndDate(request.endDate());
        reservation.setNotes(request.notes());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.recalculatePricing();
        return reservation;
    }

    public ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEquipment().getId(),
                reservation.getEquipment().getName(),
                reservation.getRenter().getId(),
                reservation.getRenter().getName(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getTotalDays(),
                reservation.getDailyRate(),
                reservation.getRentalAmount(),
                reservation.getSecurityDeposit(),
                reservation.getTotalAmount(),
                reservation.getCurrency(),
                reservation.getStatus(),
                reservation.getNotes(),
                PaymentService.paymentStatusOf(reservation),
                reservation.getCreatedAt()
        );
    }
}
