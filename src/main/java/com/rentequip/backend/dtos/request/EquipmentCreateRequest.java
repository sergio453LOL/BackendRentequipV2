package com.rentequip.backend.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record EquipmentCreateRequest(

        @NotBlank @Size(min = 3, max = 150)
        String name,

        @Size(max = 2000)
        String description,

        @NotBlank @Size(max = 80)
        String brand,

        @NotBlank @Size(max = 80)
        String model,

        @Min(1950) @Max(2100)
        Integer manufactureYear,

        @Size(max = 100)
        String serialNumber,

        @Size(max = 4000)
        String specifications,

        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal dailyRate,

        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal weeklyRate,

        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
        BigDecimal securityDeposit,

        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
        String currency,

        @Size(max = 255)
        String address,

        @NotBlank @Size(max = 100)
        String city,

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 2, fraction = 7)
        BigDecimal latitude,

        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 7)
        BigDecimal longitude,

        @Size(max = 10)
        List<@NotBlank @Size(max = 500) String> imageUrls,

        @NotEmpty
        Set<@NotNull Long> categoryIds,

        @NotNull
        Long ownerCompanyId,

        /** Optional; absent means the rate does not include an operator. */
        Boolean operatorIncluded,

        @Min(0) @Max(200000)
        Integer usageHours,

        @Size(max = 500)
        @Pattern(regexp = "^(https?://.+)?$", message = "El video debe ser un enlace http(s)")
        String videoUrl,

        @Valid
        OperatorProfileRequest operator
) {
}
