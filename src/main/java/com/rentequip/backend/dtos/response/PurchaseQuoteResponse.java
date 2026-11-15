package com.rentequip.backend.dtos.response;

import java.math.BigDecimal;

/**
 * What buying the rented machine would cost right now. When the renter cannot buy it, `eligible` is false
 * and `reason` says why in a stable code (NOT_FOR_SALE, SOLD, RESERVATION_STATUS); `openRequestId` points
 * at a request already in play.
 */
public record PurchaseQuoteResponse(
        boolean eligible,
        String reason,
        BigDecimal listPrice,
        BigDecimal rentPaid,
        int rentCreditPercent,
        BigDecimal rentCredit,
        BigDecimal finalPrice,
        String currency,
        Long openRequestId
) {
}
