package com.rentequip.backend.dtos.response;

import com.rentequip.backend.enums.OfferStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OfferResponse(
        Long id,
        Long equipmentId,
        String equipmentName,
        String equipmentImageUrl,
        Long ownerCompanyId,
        String ownerCompanyName,
        Long renterCompanyId,
        String renterCompanyName,
        BigDecimal listDailyRate,
        BigDecimal proposedDailyRate,
        BigDecimal counterDailyRate,
        BigDecimal agreedDailyRate,
        String currency,
        LocalDate startDate,
        LocalDate endDate,
        String renterMessage,
        String ownerMessage,
        OfferStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
