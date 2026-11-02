package com.rentequip.backend.dtos.request;

import com.rentequip.backend.enums.ReceiptChannel;
import jakarta.validation.constraints.NotNull;

/** Sends the payment receipt of a reservation to the requesting user's registered email or phone. */
public record ReceiptDeliveryRequest(
        @NotNull
        ReceiptChannel channel
) {
}
