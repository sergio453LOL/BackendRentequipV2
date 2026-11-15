package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * An answer in the negotiation. A counter needs {@code dailyRate}; accepting or rejecting only carries an
 * optional message.
 */
public record OfferReplyRequest(

        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal dailyRate,

        @Size(max = 500)
        String message
) {
}
