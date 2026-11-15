package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Puts a machine up for sale, changes its terms, or (with a null price) takes it off sale. The credit is
 * the share of the rent a renter already paid that comes off the price when they buy.
 */
public record EquipmentSaleRequest(
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal salePrice,

        @NotNull @Min(0) @Max(100)
        Integer rentCreditPercent
) {
}
