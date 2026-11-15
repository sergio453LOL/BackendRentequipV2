package com.rentequip.backend.identity;

import com.rentequip.backend.exceptions.NotificationDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SUNAT (RUC) and RENIEC (DNI) lookups. Without a token it uses the free apis.net.pe v1 endpoints, so typing
 * a RUC fills in the company straight away; with a Decolecta token (the paid successor) it uses that one.
 * Reads both generations' field names.
 */
@Component
public class IdentityLookupClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityLookupClient.class);

    private final RestClient client;
    private final String token;

    @Autowired
    public IdentityLookupClient(@Value("${rentequip.identity.base-url}") String baseUrl,
                                @Value("${rentequip.identity.free-url}") String freeUrl,
                                @Value("${rentequip.identity.token}") String token) {
        this(RestClient.builder().requestFactory(timeouts()), token.isBlank() ? freeUrl : baseUrl, token);
    }

    /** Tests bind a mock server to the builder. */
    IdentityLookupClient(RestClient.Builder builder, String baseUrl, String token) {
        this.token = token.trim();
        this.client = baseUrl.isBlank() ? null : builder.baseUrl(baseUrl.trim()).build();
    }

    /** The registration form waits on this call: a slow registry must not hang it. */
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(6));
        return factory;
    }

    public boolean isConfigured() {
        return client != null;
    }

    /** Free endpoints have their own paths and no token. */
    private boolean isFree() {
        return token.isEmpty();
    }

    /** Empty when SUNAT does not know the number; throws when the service cannot answer. */
    public Optional<RucInfo> findRuc(String ruc) {
        return get(isFree() ? "/v1/ruc" : "/v1/sunat/ruc", ruc).map(body -> new RucInfo(ruc,
                text(body, "razon_social", "razonSocial", "nombre"),
                text(body, "estado"),
                text(body, "condicion"),
                text(body, "direccion"),
                text(body, "distrito"),
                text(body, "provincia"),
                text(body, "departamento"),
                RucValidator.typeOf(ruc)));
    }

    public Optional<DniInfo> findDni(String dni) {
        return get(isFree() ? "/v1/dni" : "/v1/reniec/dni", dni).map(body -> {
            String firstNames = text(body, "first_name", "nombres");
            String lastNames = Stream.of(text(body, "first_last_name", "apellidoPaterno"), text(body, "second_last_name", "apellidoMaterno"))
                    .filter(part -> part != null && !part.isBlank())
                    .collect(Collectors.joining(" "));
            String fullName = text(body, "full_name", "nombre");
            return new DniInfo(dni, firstNames, lastNames, fullName != null ? fullName : (firstNames + " " + lastNames).trim());
        });
    }

    private Optional<Map<?, ?>> get(String path, String number) {
        if (!isConfigured()) {
            throw new NotificationDeliveryException(HttpStatus.SERVICE_UNAVAILABLE, "CHANNEL_NOT_CONFIGURED",
                    "La consulta a SUNAT/RENIEC no está configurada en este servidor.");
        }
        try {
            Map<?, ?> body = client.get()
                    .uri(uri -> uri.path(path).queryParam("numero", number).build())
                    .headers(headers -> {
                        if (!isFree()) headers.setBearerAuth(token);
                    })
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Map.class);
            return body == null || body.isEmpty() || body.containsKey("error") ? Optional.empty() : Optional.of(body);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404 || status == 422 || status == 400) {
                return Optional.empty();
            }
            log.warn("Identity lookup {} refused: HTTP {}", path, status);
            throw NotificationDeliveryException.failed(status == 401 || status == 403
                    ? "El token de SUNAT/RENIEC no es válido."
                    : "SUNAT/RENIEC no respondió. Inténtalo en unos minutos.");
        } catch (RuntimeException e) {
            log.warn("Could not reach the identity lookup: {}", e.getClass().getSimpleName());
            throw NotificationDeliveryException.failed("No pudimos conectarnos con SUNAT/RENIEC. Inténtalo en unos minutos.");
        }
    }

    private static String text(Map<?, ?> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return null;
    }
}
