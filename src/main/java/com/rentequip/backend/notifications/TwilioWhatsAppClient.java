package com.rentequip.backend.notifications;

import com.rentequip.backend.exceptions.NotificationDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends WhatsApp messages through Twilio's REST API (no SDK: one form POST). It authenticates with an API key
 * (SK...) and its secret, which can be revoked without touching the account's master token. In Twilio's
 * WhatsApp sandbox only numbers that sent the "join ..." code can receive messages.
 */
public class TwilioWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppClient.class);

    private static final Pattern TWILIO_ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");

    private static final String JOIN_SANDBOX =
            "Tu número aún no está unido al WhatsApp de prueba de Twilio (o pasaron más de 24 h desde tu último mensaje). "
                    + "Envía el código \"join ...\" al número de WhatsApp de RentEquip y vuelve a intentarlo.";

    /**
     * Twilio error codes the user can act on, translated. Anything else gets a generic message. Outside the
     * 24 h window opened by the user's last message, the sandbox only accepts templates, hence 21654 and 63016.
     */
    private static final Map<Integer, String> ERROR_MESSAGES = Map.of(
            63015, JOIN_SANDBOX,
            21654, JOIN_SANDBOX,
            63016, JOIN_SANDBOX,
            63007, "El número de WhatsApp de RentEquip no está habilitado en Twilio.",
            21211, "El celular registrado no es un número válido.",
            21614, "El celular registrado no puede recibir mensajes.",
            20003, "Las credenciales de Twilio no son válidas.");

    private final RestClient client;
    private final String accountSid;
    private final String from;
    private final boolean configured;

    public TwilioWhatsAppClient(String accountSid, String apiKeySid, String apiKeySecret, String authToken, String from) {
        this.accountSid = accountSid.trim();
        this.from = from.trim();
        // The account's Auth Token, when given, wins: it always matches the account SID, while an API key can
        // belong to another (sub)account or have lost its secret, which Twilio shows only once.
        boolean useAuthToken = !authToken.isBlank();
        String username = useAuthToken ? this.accountSid : apiKeySid.trim();
        String password = useAuthToken ? authToken.trim() : apiKeySecret.trim();
        this.configured = !this.accountSid.isEmpty() && !username.isEmpty() && !password.isEmpty() && !this.from.isEmpty();
        String credentials = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.client = RestClient.builder()
                .baseUrl("https://api.twilio.com/2010-04-01")
                .defaultHeader("Authorization", "Basic " + credentials)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public void send(String toE164, String body) {
        if (!configured) {
            throw NotificationDeliveryException.notConfigured("WhatsApp");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", "whatsapp:" + from);
        form.add("To", "whatsapp:" + toE164);
        form.add("Body", body);
        try {
            client.post()
                    .uri("/Accounts/{sid}/Messages.json", accountSid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("WhatsApp sent to {}", toE164);
        } catch (RestClientResponseException e) {
            Integer code = errorCode(e.getResponseBodyAsString());
            log.warn("Twilio refused WhatsApp to {}: HTTP {} code {}", toE164, e.getStatusCode().value(), code);
            // Map.of rejects null keys, so an unparseable body goes straight to the generic message.
            String message = code == null ? null : ERROR_MESSAGES.get(code);
            throw NotificationDeliveryException.failed(
                    message != null ? message : "WhatsApp no aceptó el mensaje. Inténtalo en unos minutos.");
        } catch (RestClientException e) {
            log.warn("Could not reach Twilio: {}", e.getMessage());
            throw NotificationDeliveryException.failed("No pudimos conectarnos con WhatsApp. Inténtalo en unos minutos.");
        }
    }

    private static Integer errorCode(String body) {
        Matcher matcher = TWILIO_ERROR_CODE.matcher(body == null ? "" : body);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
