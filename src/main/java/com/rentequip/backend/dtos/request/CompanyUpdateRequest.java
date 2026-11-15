package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Partial update: a null field means "keep the current value". The tax ID is immutable.
 */
public record CompanyUpdateRequest(

        @Size(min = 2, max = 150)
        String name,

        @Email @Size(max = 150)
        String email,

        @Size(max = 20)
        String phone,

        @Size(max = 255)
        String address,

        @Size(max = 100)
        String city,

        @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 2, fraction = 7)
        BigDecimal latitude,

        @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 7)
        BigDecimal longitude,

        /** Empty string removes the logo. Only http(s) URLs, as returned by the upload endpoint. */
        @Size(max = 500) @Pattern(regexp = "^(https?://\\S+)?$", message = "Logo must be an http(s) URL")
        String logoUrl,

        @Size(max = 1000)
        String description
) {
}
