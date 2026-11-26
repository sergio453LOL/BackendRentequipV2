package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The ID token ("credential") that Google Identity Services returned to the web. */
public record GoogleLoginRequest(

        @NotBlank @Size(max = 4096)
        String credential
) {
}
