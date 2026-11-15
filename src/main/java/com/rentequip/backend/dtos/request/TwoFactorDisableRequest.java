package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Turning the protection off asks for the password, so an unattended open session cannot do it. */
public record TwoFactorDisableRequest(@NotBlank @Size(max = 64) String password) {
}
