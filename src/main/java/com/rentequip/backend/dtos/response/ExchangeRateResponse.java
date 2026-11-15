package com.rentequip.backend.dtos.response;

import java.math.BigDecimal;

/**
 * How many units of {@code quote} one {@code base} buys (soles per dollar). {@code updatedAt} is the
 * provider's own date, null when the configured fallback is in use.
 */
public record ExchangeRateResponse(
        String base,
        String quote,
        BigDecimal rate,
        String updatedAt,
        String source
) {
}
