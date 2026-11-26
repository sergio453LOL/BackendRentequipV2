package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank @Email @Size(max = 150)
        String email,

        @NotBlank @Size(max = 64)
        String password,

        /** reCAPTCHA v3 token from the web login (action "login"). The mobile app does not send one. */
        @Size(max = 4096)
        String recaptchaToken
) {

    public LoginRequest(String email, String password) {
        this(email, password, null);
    }
}
