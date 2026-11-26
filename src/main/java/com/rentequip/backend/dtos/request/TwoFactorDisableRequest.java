package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Turning the protection off proves it is the owner, so an unattended open session cannot do it: with the
 * password, or, for an account without one (created with Google), with a code from
 * {@code POST /users/me/account-code} sent to its email.
 */
public record TwoFactorDisableRequest(
        @Size(max = 64)
        String password,

        @Size(max = 36)
        String challengeId,

        @Pattern(regexp = "\\d{6}", message = "El código tiene 6 dígitos")
        String code
) {
    public TwoFactorDisableRequest(String password) {
        this(password, null, null);
    }
}
