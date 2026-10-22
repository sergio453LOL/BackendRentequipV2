package com.rentequip.backend.payments;

import java.math.BigDecimal;

/**
 * Port to the card processor. The backend only ever sees a single-use token created in the browser
 * (Stripe-style tokenization), never the card number, so the API stays out of PCI scope.
 */
public interface PaymentGateway {

    /** Captures the amount immediately (the rental). */
    GatewayResult charge(String paymentToken, BigDecimal amount, String currency, String description);

    /** Reserves the amount on the card without capturing it (the security deposit). */
    GatewayResult authorize(String paymentToken, BigDecimal amount, String currency, String description);

    /** Returns a captured charge to the card. */
    void refund(String providerReference);

    /** Drops an authorization hold, so the reserved amount is never charged. */
    void release(String providerReference);

    record GatewayResult(boolean approved, String reference, String declineReason) {

        public static GatewayResult approved(String reference) {
            return new GatewayResult(true, reference, null);
        }

        public static GatewayResult declined(String reason) {
            return new GatewayResult(false, null, reason);
        }
    }
}
