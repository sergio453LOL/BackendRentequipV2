package com.rentequip.backend.dtos.response;

import com.rentequip.backend.enums.ReceiptChannel;

/** Where the receipt went, partially masked (s***@gmail.com, +51 9•• ••• 321) so the screen can confirm it. */
public record ReceiptDeliveryResponse(
        ReceiptChannel channel,
        String destination
) {
}
