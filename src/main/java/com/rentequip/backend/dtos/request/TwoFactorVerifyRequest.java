package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TwoFactorVerifyRequest(

        @NotBlank @Size(max = 36)
        String challengeId,

        @NotBlank @Pattern(regexp = "\\d{6}", message = "El código tiene 6 dígitos")
        String code
) {
}
