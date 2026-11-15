package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The current password proves it is the owner of the session; the new one follows the sign-up rules. */
public record PasswordChangeRequest(
        @NotBlank
        String currentPassword,

        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one lowercase letter, one uppercase letter and one digit"
        )
        String newPassword
) {
}
