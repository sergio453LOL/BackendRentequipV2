package com.rentequip.backend.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sign-up with Google: the same company and administrator as {@link RegisterRequest}, minus the email and
 * the password. The email is the one Google verified in the credential, and the account signs in with Google.
 */
public record GoogleRegisterRequest(

        @NotBlank @Size(max = 4096)
        String credential,

        @NotNull @Valid
        CompanyCreateRequest company,

        @NotBlank @Size(max = 80)
        String firstName,

        @NotBlank @Size(max = 80)
        String lastName,

        @Size(max = 20)
        String phone
) {
}
