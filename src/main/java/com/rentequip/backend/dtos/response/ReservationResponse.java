package com.rentequip.backend.dtos.response;

import com.rentequip.backend.enums.ReservationPaymentStatus;
import com.rentequip.backend.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long equipmentId,
        String equipmentName,
        Long renterCompanyId,
        String renterCompanyName,
        LocalDate startDate,
        LocalDate endDate,
        Integer totalDays,
        BigDecimal dailyRate,
        BigDecimal rentalAmount,
        BigDecimal securityDeposit,
        BigDecimal totalAmount,
        String currency,
        ReservationStatus status,
        String notes,
        ReservationPaymentStatus paymentStatus,
        LocalDateTime createdAt
) {
}
