package com.rentequip.backend.services;

import com.rentequip.backend.dtos.response.ExchangeRateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Soles per US dollar, so the catalogue can show every price in both currencies. The rate comes from a
 * public exchange-rate API (no key) and is cached for hours; when the API cannot be reached the last known
 * rate, or the configured one, is used, so prices never disappear.
 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final RestClient client;
    private final BigDecimal fallbackRate;

    private volatile ExchangeRateResponse cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    public ExchangeRateService(@Value("${rentequip.exchange.url}") String url,
                               @Value("${rentequip.exchange.usd-pen-fallback}") BigDecimal fallbackRate) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(4));
        this.client = url.isBlank() ? null : RestClient.builder().baseUrl(url).requestFactory(timeouts).build();
        this.fallbackRate = fallbackRate;
    }

    public ExchangeRateResponse usdToPen() {
        ExchangeRateResponse current = cached;
        if (current != null && Instant.now().isBefore(cachedAt.plus(CACHE_TTL))) {
            return current;
        }
        ExchangeRateResponse fresh = fetch();
        if (fresh != null) {
            cached = fresh;
            cachedAt = Instant.now();
            return fresh;
        }
        // Keep serving the last good rate; only without one fall back to the configured value.
        return current != null ? current : new ExchangeRateResponse("USD", "PEN", fallbackRate, null, "config");
    }

    private ExchangeRateResponse fetch() {
        if (client == null) {
            return null;
        }
        try {
            Map<?, ?> body = client.get().retrieve().body(Map.class);
            Object rates = body == null ? null : body.get("rates");
            Object pen = rates instanceof Map<?, ?> map ? map.get("PEN") : null;
            if (!(pen instanceof Number number)) {
                log.warn("The exchange-rate API answered without a PEN rate");
                return null;
            }
            Object updated = body.get("time_last_update_utc");
            return new ExchangeRateResponse("USD", "PEN", new BigDecimal(number.toString()).setScale(4, RoundingMode.HALF_UP),
                    updated == null ? null : updated.toString(), "open.er-api.com");
        } catch (RuntimeException e) {
            // RestClientException or a body that does not parse: either way, keep the last known rate.
            log.warn("Could not reach the exchange-rate API: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
