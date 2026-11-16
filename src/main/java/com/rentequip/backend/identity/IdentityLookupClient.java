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
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SUNAT (RUC) and RENIEC (DNI) lookups, tried in order until one answers:
 * <ol>
 *   <li>apisperu (dniruc.apisperu.com), when {@code APISPERU_TOKEN} is set;</li>
 *   <li>Decolecta (the paid successor of apis.net.pe), when {@code IDENTITY_API_TOKEN} is set;</li>
 *   <li>the free apis.net.pe v1 endpoints, always last, so a rate-limited or expired token still fills the form.</li>
 * </ol>
 * "Not registered" is an answer and stops the chain; only errors (timeouts, 429, bad token) move on to the next
 * provider. Reads the field names of all three.
 */
@Component
public class IdentityLookupClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityLookupClient.class);

    enum Kind { APISPERU, DECOLECTA, FREE }

    record Provider(Kind kind, RestClient client, String token) {
    }

    private final List<Provider> providers;

    @Autowired
    public IdentityLookupClient(@Value("${rentequip.identity.base-url}") String baseUrl,
                                @Value("${rentequip.identity.free-url}") String freeUrl,
                                @Value("${rentequip.identity.token}") String token,
                                @Value("${rentequip.identity.apisperu-url}") String apisperuUrl,
                                @Value("${rentequip.identity.apisperu-token}") String apisperuToken) {
        List<Provider> chain = new ArrayList<>();
        if (!apisperuToken.isBlank() && !apisperuUrl.isBlank()) {
            chain.add(provider(Kind.APISPERU, RestClient.builder().requestFactory(timeouts()), apisperuUrl, apisperuToken));
        }
        if (!token.isBlank() && !baseUrl.isBlank()) {
            chain.add(provider(Kind.DECOLECTA, RestClient.builder().requestFactory(timeouts()), baseUrl, token));
        }
        if (!freeUrl.isBlank()) {
            chain.add(provider(Kind.FREE, RestClient.builder().requestFactory(timeouts()), freeUrl, ""));
        }
        this.providers = List.copyOf(chain);
    }

    /** Tests bind a mock server to the builder: a token means Decolecta, no token the free endpoints. */
    IdentityLookupClient(RestClient.Builder builder, String baseUrl, String token) {
        this(baseUrl.isBlank() ? List.of()
                : List.of(provider(token.isBlank() ? Kind.FREE : Kind.DECOLECTA, builder, baseUrl, token)));
    }

    IdentityLookupClient(List<Provider> providers) {
        this.providers = List.copyOf(providers);
    }

    static Provider provider(Kind kind, RestClient.Builder builder, String baseUrl, String token) {
        return new Provider(kind, builder.baseUrl(baseUrl.trim()).build(), token.trim());
    }

    /** The registration form waits on this call: a slow registry must not hang it. */
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(6));
        return factory;
    }

    public boolean isConfigured() {
        return !providers.isEmpty();
    }

    /** Empty when SUNAT does not know the number; throws when no service can answer. */
    public Optional<RucInfo> findRuc(String ruc) {
        return lookup("ruc", ruc).map(body -> new RucInfo(ruc,
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
        return lookup("dni", dni).map(body -> {
            String firstNames = text(body, "first_name", "nombres");
            String lastNames = Stream.of(text(body, "first_last_name", "apellidoPaterno"), text(body, "second_last_name", "apellidoMaterno"))
                    .filter(part -> part != null && !part.isBlank())
                    .collect(Collectors.joining(" "));
            String fullName = text(body, "full_name", "nombre");
            return new DniInfo(dni, firstNames, lastNames, fullName != null ? fullName : (firstNames + " " + lastNames).trim());
        });
    }

    /** {@code type} is "ruc" or "dni". */
    private Optional<Map<?, ?>> lookup(String type, String number) {
        if (!isConfigured()) {
            throw new NotificationDeliveryException(HttpStatus.SERVICE_UNAVAILABLE, "CHANNEL_NOT_CONFIGURED",
                    "La consulta a SUNAT/RENIEC no está configurada en este servidor.");
        }
        RuntimeException lastFailure = null;
        for (Provider provider : providers) {
            try {
                return get(provider, type, number);
            } catch (RuntimeException failure) {
                lastFailure = failure;
            }
        }
        throw lastFailure;
    }

    private Optional<Map<?, ?>> get(Provider provider, String type, String number) {
        Function<UriBuilder, URI> uri = switch (provider.kind()) {
            // apisperu: /api/v1/ruc/{numero}?token=...
            case APISPERU -> builder -> builder.path("/api/v1/{type}/{number}").queryParam("token", provider.token()).build(type, number);
            case DECOLECTA -> builder -> builder.path(type.equals("ruc") ? "/v1/sunat/ruc" : "/v1/reniec/dni").queryParam("numero", number).build();
            case FREE -> builder -> builder.path("/v1/" + type).queryParam("numero", number).build();
        };
        try {
            Map<?, ?> body = provider.client().get()
                    .uri(uri)
                    .headers(headers -> {
                        if (provider.kind() == Kind.DECOLECTA) headers.setBearerAuth(provider.token());
                    })
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(Map.class);
            boolean missing = body == null || body.isEmpty() || body.containsKey("error")
                    || Boolean.FALSE.equals(body.get("success"));
            return missing ? Optional.empty() : Optional.of(body);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404 || status == 422 || status == 400) {
                return Optional.empty();
            }
            log.warn("Identity lookup {} via {} refused: HTTP {}", type, provider.kind(), status);
            throw NotificationDeliveryException.failed(status == 401 || status == 403
                    ? "El token de SUNAT/RENIEC no es válido."
                    : "SUNAT/RENIEC no respondió. Inténtalo en unos minutos.");
        } catch (RuntimeException e) {
            log.warn("Could not reach the identity lookup {} via {}: {}", type, provider.kind(), e.getClass().getSimpleName());
            throw NotificationDeliveryException.failed("No pudimos conectarnos con SUNAT/RENIEC. Inténtalo en unos minutos.");
        }
    }

    /** SUNAT writes "-" for a missing address (common on RUC 10): that is no value. */
    private static String text(Map<?, ?> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !value.toString().isBlank() && !value.toString().trim().equals("-")) {
                return value.toString().trim();
            }
        }
        return null;
    }
}
