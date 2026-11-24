package com.rentequip.backend.dtos.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Signs an address up for offers; the consent box must be ticked. */
public record NewsletterSubscriptionRequest(
        @NotBlank(message = "Escribe tu correo")
        @Email(message = "Escribe un correo válido")
        @Size(max = 160, message = "El correo es demasiado largo")
        String email,

        @AssertTrue(message = "Acepta recibir ofertas para suscribirte")
        boolean consent
) {
}
