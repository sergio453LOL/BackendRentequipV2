package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The current password proves it is the owner of the session; the new one follows the sign-up rules. An
 * account without a password (created with Google) sets its first one with a code from
 * {@code POST /users/me/account-code} instead: {@code challengeId} and {@code code}.
 */
public record PasswordChangeRequest(
        @Size(max = 64)
        String currentPassword,

        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one lowercase letter, one uppercase letter and one digit"
        )
        String newPassword,

        @Size(max = 36)
        String challengeId,

        @Pattern(regexp = "\\d{6}", message = "El código tiene 6 dígitos")
        String code
) {
    public PasswordChangeRequest(String currentPassword, String newPassword) {
        this(currentPassword, newPassword, null, null);
    }
}
