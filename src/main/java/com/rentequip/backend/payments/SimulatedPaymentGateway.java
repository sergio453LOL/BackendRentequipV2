package com.rentequip.backend.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Sandbox processor that behaves like Stripe's test mode: it accepts Stripe's test tokens and declines
 * the same ones Stripe declines, without moving real money. A Stripe-backed implementation of
 * {@link PaymentGateway} can replace it without touching the reservation flow.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    private static final Map<String, String> DECLINED_TOKENS = Map.of(
            "tok_chargeDeclined", "La tarjeta fue rechazada por el banco emisor.",
            "tok_chargeDeclinedInsufficientFunds", "La tarjeta no tiene fondos suficientes.",
            "tok_chargeDeclinedExpiredCard", "La tarjeta está vencida.",
            "tok_chargeDeclinedIncorrectCvc", "El código de seguridad (CVC) es incorrecto.");

    @Override
    public GatewayResult charge(String paymentToken, BigDecimal amount, String currency, String description) {
        return process(paymentToken, "sim_ch_", amount, currency, description);
    }

    @Override
    public GatewayResult authorize(String paymentToken, BigDecimal amount, String currency, String description) {
        return process(paymentToken, "sim_auth_", amount, currency, description);
    }

    @Override
    public void refund(String providerReference) {
        log.info("[simulated gateway] refunded {}", providerReference);
    }

    @Override
    public void release(String providerReference) {
        log.info("[simulated gateway] released hold {}", providerReference);
    }

    private GatewayResult process(String paymentToken, String referencePrefix, BigDecimal amount,
                                  String currency, String description) {
        if (paymentToken == null || !paymentToken.startsWith("tok_")) {
            return GatewayResult.declined("El método de pago no es válido.");
        }
        String declineReason = DECLINED_TOKENS.get(paymentToken);
        if (declineReason != null) {
            log.info("[simulated gateway] declined {} {} for {}: {}", amount, currency, description, declineReason);
            return GatewayResult.declined(declineReason);
        }
        String reference = referencePrefix + UUID.randomUUID().toString().replace("-", "");
        log.info("[simulated gateway] {} {} {} for {}", reference, amount, currency, description);
        return GatewayResult.approved(reference);
    }
}
