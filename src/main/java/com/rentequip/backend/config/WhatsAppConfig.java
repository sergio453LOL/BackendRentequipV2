package com.rentequip.backend.config;

import com.rentequip.backend.notifications.TwilioWhatsAppClient;
import com.rentequip.backend.notifications.WhatsAppClient;
import com.rentequip.backend.notifications.WhatsAppEmpresasClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the WhatsApp provider: WhatsApp Empresas when its API key is set (it sends free text), Twilio
 * otherwise. With neither configured, sending a receipt by WhatsApp answers 503.
 */
@Configuration
public class WhatsAppConfig {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppConfig.class);

    @Bean
    public WhatsAppClient whatsAppClient(@Value("${rentequip.whatsapp-empresas.base-url}") String empresasBaseUrl,
                                         @Value("${rentequip.whatsapp-empresas.access-token}") String empresasToken,
                                         @Value("${rentequip.twilio.account-sid}") String accountSid,
                                         @Value("${rentequip.twilio.api-key-sid}") String apiKeySid,
                                         @Value("${rentequip.twilio.api-key-secret}") String apiKeySecret,
                                         @Value("${rentequip.twilio.auth-token}") String authToken,
                                         @Value("${rentequip.twilio.whatsapp-from}") String from) {
        if (!empresasToken.isBlank()) {
            log.info("WhatsApp receipts go through WhatsApp Empresas");
            return new WhatsAppEmpresasClient(empresasBaseUrl, empresasToken);
        }
        TwilioWhatsAppClient twilio = new TwilioWhatsAppClient(accountSid, apiKeySid, apiKeySecret, authToken, from);
        log.info(twilio.isConfigured() ? "WhatsApp receipts go through Twilio" : "WhatsApp is not configured");
        return twilio;
    }
}
