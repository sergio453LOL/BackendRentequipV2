package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The renter's proposal. Dates are optional: a price can be negotiated before choosing them. */
public record OfferCreateRequest(

        @NotNull
        Long equipmentId,

        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal dailyRate,

        @FutureOrPresent
        LocalDate startDate,

        @FutureOrPresent
        LocalDate endDate,

        @Size(max = 500)
        String message
) {

    @AssertTrue(message = "End date must be on or after start date")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
