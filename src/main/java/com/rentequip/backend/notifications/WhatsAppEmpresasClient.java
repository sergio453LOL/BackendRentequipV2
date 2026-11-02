package com.rentequip.backend.notifications;

import com.rentequip.backend.exceptions.NotificationDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.regex.Pattern;

/**
 * Sends WhatsApp messages through WhatsApp Empresas (api.whatsappempresas.com), a gateway built on Baileys:
 * it drives a WhatsApp account linked by QR in its panel, so it can send free text without Meta templates.
 * The API key travels as a query parameter, which is why nothing here logs the request URL.
 */
public class WhatsAppEmpresasClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppEmpresasClient.class);

    /** The gateway answers 200 even when it rejects a message; the body then says so. */
    private static final Pattern REJECTED = Pattern.compile(
            "\"(status|success)\"\\s*:\\s*(\"?(error|failed|false)\"?)", Pattern.CASE_INSENSITIVE);

    private final RestClient client;
    private final String accessToken;

    public WhatsAppEmpresasClient(String baseUrl, String accessToken) {
        this.accessToken = accessToken.trim();
        this.client = RestClient.builder().baseUrl(baseUrl.trim()).build();
    }

    @Override
    public boolean isConfigured() {
        return !accessToken.isEmpty();
    }

    @Override
    public void send(String toE164, String body) {
        if (!isConfigured()) {
            throw NotificationDeliveryException.notConfigured("WhatsApp");
        }
        // The gateway wants the country code without "+": 51987654321.
        String number = toE164.replace("+", "");
        String response;
        try {
            response = client.get()
                    .uri(uri -> uri.path("/api/send")
                            .queryParam("number", number)
                            .queryParam("type", "text")
                            .queryParam("message", "{message}")
                            .queryParam("access_token", "{token}")
                            .build(body, accessToken))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("WhatsApp Empresas refused the message to {}: HTTP {}", toE164, e.getStatusCode().value());
            throw NotificationDeliveryException.failed(e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403
                    ? "La API key de WhatsApp no es válida."
                    : "WhatsApp no aceptó el mensaje. Inténtalo en unos minutos.");
        } catch (RestClientException e) {
            // The exception message contains the URL, and with it the API key: log only its type.
            log.warn("Could not reach WhatsApp Empresas: {}", e.getClass().getSimpleName());
            throw NotificationDeliveryException.failed("No pudimos conectarnos con WhatsApp. Inténtalo en unos minutos.");
        }
        if (response != null && REJECTED.matcher(response).find()) {
            log.warn("WhatsApp Empresas rejected the message to {}: {}", toE164, response);
            throw NotificationDeliveryException.failed(
                    "WhatsApp no aceptó el mensaje. Revisa que el dispositivo siga conectado en el panel.");
        }
        log.info("WhatsApp sent to {}", toE164);
    }
}
