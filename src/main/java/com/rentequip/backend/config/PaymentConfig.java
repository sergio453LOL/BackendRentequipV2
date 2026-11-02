package com.rentequip.backend.config;

import com.rentequip.backend.payments.PaymentGateway;
import com.rentequip.backend.payments.SimulatedPaymentGateway;
import com.rentequip.backend.payments.StripePaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the card processor: Stripe when a secret key is configured, the simulated gateway otherwise, so
 * the project still boots and its tests run without a Stripe account.
 */
@Configuration
public class PaymentConfig {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfig.class);

    @Bean
    public PaymentGateway paymentGateway(@Value("${rentequip.payments.stripe.secret-key}") String stripeSecretKey) {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            log.info("STRIPE_SECRET_KEY is not set: payments use the simulated gateway");
            return new SimulatedPaymentGateway();
        }
        if (stripeSecretKey.startsWith("sk_live_")) {
            log.warn("STRIPE_SECRET_KEY is a live key: payments will move real money");
        }
        return new StripePaymentGateway(stripeSecretKey);
    }
}
