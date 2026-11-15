package com.rentequip.backend.dtos.response;

import com.rentequip.backend.enums.PurchaseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PurchaseRequestResponse(
        Long id,
        Long reservationId,
        Long equipmentId,
        String equipmentName,
        String equipmentImageUrl,
        Long buyerCompanyId,
        String buyerCompanyName,
        Long sellerCompanyId,
        String sellerCompanyName,
        BigDecimal listPrice,
        BigDecimal rentCredit,
        BigDecimal finalPrice,
        String currency,
        PurchaseStatus status,
        LocalDateTime createdAt,
        LocalDateTime decidedAt,
        LocalDateTime paidAt
) {
}
