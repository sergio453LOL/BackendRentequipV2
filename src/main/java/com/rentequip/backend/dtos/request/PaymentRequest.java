package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pays a reservation with a single-use token created by the client-side card form. The card number never
 * reaches the backend.
 */
public record PaymentRequest(
        @NotBlank @Size(max = 100)
        String paymentToken
) {
}
