package com.rentequip.backend.notifications;

import com.rentequip.backend.exceptions.NotificationDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * Sends SMS through an SMS Gateway server (Android phones linked as senders, REST API at
 * {@code /api/v1/messages/send}). The gateway documents the call as a GET whose JSON body carries the
 * parameters; a GET body is dropped by many proxies, so they travel in the query string, which Laravel
 * reads the same way. The API key goes in the Authorization header.
 */
@Component
public class SmsGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(SmsGatewayClient.class);

    private final RestClient client;
    private final String apiKey;
    private final List<String> sims;

    @Autowired
    public SmsGatewayClient(@Value("${rentequip.sms-gateway.base-url}") String baseUrl,
                            @Value("${rentequip.sms-gateway.api-key}") String apiKey,
                            @Value("${rentequip.sms-gateway.sims}") String sims) {
        this(RestClient.builder(), baseUrl, apiKey, sims);
    }

    /** Tests bind a mock server to the builder. */
    SmsGatewayClient(RestClient.Builder builder, String baseUrl, String apiKey, String sims) {
        this.apiKey = apiKey.trim();
        this.sims = Arrays.stream(sims.split(",")).map(String::trim).filter(id -> !id.isEmpty()).toList();
        this.client = baseUrl.isBlank() ? null : builder.baseUrl(baseUrl.trim()).build();
    }

    public boolean isConfigured() {
        return client != null && !apiKey.isEmpty();
    }

    /** Sends a text SMS to a number in E.164 format (+51987654321). */
    public void send(String toE164, String body) {
        if (!isConfigured()) {
            throw NotificationDeliveryException.notConfigured("SMS");
        }
        try {
            client.get()
                    .uri(uri -> buildUri(uri, toE164, body))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            // Only the start of the body: a wrong URL answers with a whole HTML page.
            String reply = e.getResponseBodyAsString();
            log.warn("SMS gateway refused the message to {}: HTTP {} {}", toE164, status,
                    reply.length() > 300 ? reply.substring(0, 300) + "..." : reply);
            throw NotificationDeliveryException.failed(switch (status) {
                case 401, 403 -> "La API key del servicio de SMS no es válida.";
                case 422 -> "El servicio de SMS rechazó el número o el mensaje.";
                default -> "El servicio de SMS no aceptó el mensaje. Inténtalo en unos minutos.";
            });
        } catch (RestClientException e) {
            log.warn("Could not reach the SMS gateway: {}", e.getClass().getSimpleName());
            throw NotificationDeliveryException.failed("No pudimos conectarnos con el servicio de SMS. Inténtalo en unos minutos.");
        }
        log.info("SMS sent to {}", toE164);
    }

    private java.net.URI buildUri(UriBuilder uri, String toE164, String body) {
        uri.path("/api/v1/messages/send")
                .queryParam("type", "SMS")
                .queryParam("mobile_numbers[]", "{to}")
                .queryParam("message", "{message}");
        // The SIM ids come from the gateway panel (Devices > SIMs); without them the gateway uses its default.
        sims.forEach(sim -> uri.queryParam("sims[]", sim));
        return uri.build(toE164, body);
    }
}
