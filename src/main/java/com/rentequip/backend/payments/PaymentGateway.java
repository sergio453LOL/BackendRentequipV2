package com.rentequip.backend.payments;

import java.math.BigDecimal;

/**
 * Port to the card processor. The backend only ever sees a single-use token created in the browser
 * (Stripe-style tokenization), never the card number, so the API stays out of PCI scope.
 */
public interface PaymentGateway {

    /**
     * Turns the client's single-use token into a source that can pay more than once, since one checkout
     * makes two operations (rental and deposit). Declines here are reported like any other decline.
     */
    PreparedSource prepare(String paymentToken, String description);

    /** Captures the amount immediately (the rental). */
    GatewayResult charge(PaymentSource source, BigDecimal amount, String currency, String description);

    /** Reserves the amount on the card without capturing it (the security deposit). */
    GatewayResult authorize(PaymentSource source, BigDecimal amount, String currency, String description);

    /** Returns a captured charge to the card. */
    void refund(String providerReference);

    /** Drops an authorization hold, so the reserved amount is never charged. */
    void release(String providerReference);

    /** A card ready to be charged: the payment method and, for Stripe, the customer it is attached to. */
    record PaymentSource(String paymentMethod, String customer) {
    }

    record PreparedSource(PaymentSource source, String declineReason) {

        public static PreparedSource ready(PaymentSource source) {
            return new PreparedSource(source, null);
        }

        public static PreparedSource declined(String reason) {
            return new PreparedSource(null, reason);
        }

        public boolean isReady() {
            return source != null;
        }
    }

    record GatewayResult(boolean approved, String reference, String declineReason) {

        public static GatewayResult approved(String reference) {
            return new GatewayResult(true, reference, null);
        }

        public static GatewayResult declined(String reason) {
            return new GatewayResult(false, null, reason);
        }
    }
}
