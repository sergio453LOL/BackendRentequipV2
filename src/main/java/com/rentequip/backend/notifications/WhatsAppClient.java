package com.rentequip.backend.notifications;

/**
 * Sends a WhatsApp text message. {@code WhatsAppConfig} picks the provider from the configured keys, so the
 * receipt flow does not care whether the message leaves through Twilio or WhatsApp Empresas.
 */
public interface WhatsAppClient {

    boolean isConfigured();

    /** Sends a text message to a number in E.164 format (+51987654321). */
    void send(String toE164, String body);
}
