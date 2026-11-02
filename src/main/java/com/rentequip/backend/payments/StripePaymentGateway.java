package com.rentequip.backend.payments;

import com.rentequip.backend.exceptions.PaymentProviderException;
import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.PaymentMethodCreateParams;
import com.stripe.param.RefundCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * Real card processing through Stripe. The browser sends a Stripe.js payment method ({@code pm_}) or a Stripe
 * test token ({@code tok_}); the rental becomes a captured PaymentIntent and the deposit a PaymentIntent with
 * manual capture, which keeps the money held on the card until it is released.
 */
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    private static final String GENERIC_DECLINE = "La tarjeta fue rechazada. Prueba con otra tarjeta.";

    /** Stripe's decline and error codes, translated for the user. Anything else gets the generic message. */
    private static final Map<String, String> DECLINE_MESSAGES = Map.of(
            "card_declined", "La tarjeta fue rechazada por el banco emisor.",
            "insufficient_funds", "La tarjeta no tiene fondos suficientes.",
            "expired_card", "La tarjeta está vencida.",
            "incorrect_cvc", "El código de seguridad (CVC) es incorrecto.",
            "incorrect_number", "El número de tarjeta es incorrecto.",
            "processing_error", "El banco no pudo procesar la tarjeta. Inténtalo de nuevo.",
            "authentication_required", "Tu banco pide verificación adicional (3D Secure). Usa otra tarjeta.");

    private final StripeClient client;

    public StripePaymentGateway(String secretKey) {
        this.client = new StripeClient(secretKey);
    }

    /**
     * A Stripe payment method can only be used once unless it belongs to a customer, so the card is attached
     * to a new customer that both PaymentIntents of the checkout then share.
     */
    @Override
    public PreparedSource prepare(String paymentToken, String description) {
        try {
            String paymentMethod = paymentToken.startsWith("tok_")
                    ? client.v1().paymentMethods().create(PaymentMethodCreateParams.builder()
                            .setType(PaymentMethodCreateParams.Type.CARD)
                            .setCard(PaymentMethodCreateParams.Token.builder().setToken(paymentToken).build())
                            .build()).getId()
                    : paymentToken;
            String customer = client.v1().customers()
                    .create(CustomerCreateParams.builder().setDescription(description).build()).getId();
            client.v1().paymentMethods()
                    .attach(paymentMethod, PaymentMethodAttachParams.builder().setCustomer(customer).build());
            return PreparedSource.ready(new PaymentSource(paymentMethod, customer));
        } catch (CardException e) {
            return PreparedSource.declined(declineMessage(e));
        } catch (StripeException e) {
            throw providerFailure("prepare the card", e);
        }
    }

    @Override
    public GatewayResult charge(PaymentSource source, BigDecimal amount, String currency, String description) {
        return createIntent(source, amount, currency, description, PaymentIntentCreateParams.CaptureMethod.AUTOMATIC);
    }

    @Override
    public GatewayResult authorize(PaymentSource source, BigDecimal amount, String currency, String description) {
        return createIntent(source, amount, currency, description, PaymentIntentCreateParams.CaptureMethod.MANUAL);
    }

    @Override
    public void refund(String providerReference) {
        try {
            client.v1().refunds().create(RefundCreateParams.builder().setPaymentIntent(providerReference).build());
        } catch (StripeException e) {
            throw providerFailure("refund " + providerReference, e);
        }
    }

    @Override
    public void release(String providerReference) {
        try {
            client.v1().paymentIntents().cancel(providerReference, PaymentIntentCancelParams.builder().build());
        } catch (StripeException e) {
            throw providerFailure("release " + providerReference, e);
        }
    }

    private GatewayResult createIntent(PaymentSource source, BigDecimal amount, String currency, String description,
                                       PaymentIntentCreateParams.CaptureMethod captureMethod) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(toMinorUnits(amount))
                .setCurrency(currency.toLowerCase(Locale.ROOT))
                .setDescription(description)
                .setCustomer(source.customer())
                .setPaymentMethod(source.paymentMethod())
                .setCaptureMethod(captureMethod)
                .setConfirm(true)
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                        .build())
                .build();
        try {
            PaymentIntent intent = client.v1().paymentIntents().create(params);
            String expectedStatus = captureMethod == PaymentIntentCreateParams.CaptureMethod.MANUAL
                    ? "requires_capture" : "succeeded";
            if (expectedStatus.equals(intent.getStatus())) {
                return GatewayResult.approved(intent.getId());
            }
            // The checkout has no page to come back to, so a card that asks for 3D Secure is declined and its
            // intent cancelled instead of being left waiting for an authentication that will never happen.
            log.warn("Stripe PaymentIntent {} ended in status {}", intent.getId(), intent.getStatus());
            client.v1().paymentIntents().cancel(intent.getId(), PaymentIntentCancelParams.builder().build());
            return GatewayResult.declined("requires_action".equals(intent.getStatus())
                    ? DECLINE_MESSAGES.get("authentication_required") : GENERIC_DECLINE);
        } catch (CardException e) {
            return GatewayResult.declined(declineMessage(e));
        } catch (StripeException e) {
            throw providerFailure("charge " + amount + " " + currency, e);
        }
    }

    /** Stripe amounts are integers in the currency's smallest unit (céntimos for PEN). */
    private static long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static String declineMessage(CardException e) {
        String byDeclineCode = e.getDeclineCode() == null ? null : DECLINE_MESSAGES.get(e.getDeclineCode());
        return byDeclineCode != null ? byDeclineCode : DECLINE_MESSAGES.getOrDefault(e.getCode(), GENERIC_DECLINE);
    }

    private static PaymentProviderException providerFailure(String action, StripeException e) {
        log.error("Stripe failed to {}: {} (request {})", action, e.getMessage(), e.getRequestId());
        return new PaymentProviderException("No pudimos comunicarnos con el procesador de pagos. Inténtalo en unos minutos.");
    }
}
