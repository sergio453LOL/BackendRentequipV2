package com.rentequip.backend.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Sandbox processor that behaves like Stripe's test mode: it accepts Stripe's test tokens and declines
 * the same ones Stripe declines, without moving real money. It is used whenever no Stripe secret key is
 * configured, so the project still runs (and its tests pass) without a Stripe account.
 */
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    private static final Map<String, String> DECLINED_TOKENS = Map.of(
            "tok_chargeDeclined", "La tarjeta fue rechazada por el banco emisor.",
            "tok_chargeDeclinedInsufficientFunds", "La tarjeta no tiene fondos suficientes.",
            "tok_chargeDeclinedExpiredCard", "La tarjeta está vencida.",
            "tok_chargeDeclinedIncorrectCvc", "El código de seguridad (CVC) es incorrecto.");

    /** Accepts Stripe test tokens ({@code tok_}) and Stripe.js payment methods ({@code pm_}). */
    @Override
    public PreparedSource prepare(String paymentToken, String description) {
        if (paymentToken == null || !(paymentToken.startsWith("tok_") || paymentToken.startsWith("pm_"))) {
            return PreparedSource.declined("El método de pago no es válido.");
        }
        return PreparedSource.ready(new PaymentSource(paymentToken, null));
    }

    @Override
    public GatewayResult charge(PaymentSource source, BigDecimal amount, String currency, String description) {
        return process(source.paymentMethod(), "sim_ch_", amount, currency, description);
    }

    @Override
    public GatewayResult authorize(PaymentSource source, BigDecimal amount, String currency, String description) {
        return process(source.paymentMethod(), "sim_auth_", amount, currency, description);
    }

    @Override
    public void refund(String providerReference) {
        log.info("[simulated gateway] refunded {}", providerReference);
    }

    @Override
    public void release(String providerReference) {
        log.info("[simulated gateway] released hold {}", providerReference);
    }

    private GatewayResult process(String paymentMethod, String referencePrefix, BigDecimal amount,
                                  String currency, String description) {
        String declineReason = DECLINED_TOKENS.get(paymentMethod);
        if (declineReason != null) {
            log.info("[simulated gateway] declined {} {} for {}: {}", amount, currency, description, declineReason);
            return GatewayResult.declined(declineReason);
        }
        String reference = referencePrefix + UUID.randomUUID().toString().replace("-", "");
        log.info("[simulated gateway] {} {} {} for {}", reference, amount, currency, description);
        return GatewayResult.approved(reference);
    }
}
