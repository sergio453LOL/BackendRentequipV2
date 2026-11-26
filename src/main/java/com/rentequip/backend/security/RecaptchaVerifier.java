package com.rentequip.backend.security;

import com.rentequip.backend.exceptions.RecaptchaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

/**
 * Checks the reCAPTCHA v3 token of a web login with Google: it must be valid, made for the expected action
 * and score at least {@code min-score} (1.0 is surely a person, 0.0 surely a bot).
 *
 * <ul>
 *   <li>Without {@code RECAPTCHA_SECRET} nothing is checked, so local runs and tests need no keys.</li>
 *   <li>A login without a token passes unless {@code RECAPTCHA_REQUIRED=true}: the mobile app cannot run
 *       reCAPTCHA v3 and logs in through the same endpoint. A token that is sent is always checked.</li>
 *   <li>If Google cannot be reached the login goes on (logged): an outage at Google must not lock
 *       everybody out, and the password is still checked.</li>
 * </ul>
 */
@Component
public class RecaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(RecaptchaVerifier.class);

    private final String secret;
    private final double minScore;
    private final boolean required;
    private final RestClient client;

    public RecaptchaVerifier(@Value("${rentequip.security.recaptcha.secret:}") String secret,
                             @Value("${rentequip.security.recaptcha.min-score:0.5}") double minScore,
                             @Value("${rentequip.security.recaptcha.required:false}") boolean required,
                             @Value("${rentequip.security.recaptcha.verify-url:https://www.google.com/recaptcha/api/siteverify}")
                             String verifyUrl) {
        this.secret = secret == null ? "" : secret.trim();
        this.minScore = minScore;
        this.required = required;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(3));
        timeouts.setReadTimeout(Duration.ofSeconds(4));
        this.client = RestClient.builder().baseUrl(verifyUrl).requestFactory(timeouts).build();
    }

    public void check(String token, String expectedAction) {
        if (secret.isEmpty()) {
            return;
        }
        if (token == null || token.isBlank()) {
            if (required) {
                throw new RecaptchaException();
            }
            return;
        }
        Map<?, ?> verdict = siteverify(token);
        if (verdict == null) {
            return;
        }
        boolean success = Boolean.TRUE.equals(verdict.get("success"));
        Object action = verdict.get("action");
        double score = verdict.get("score") instanceof Number number ? number.doubleValue() : 0.0;
        if (!success || !expectedAction.equals(action) || score < minScore) {
            log.warn("reCAPTCHA rejected a {} (success={}, action={}, score={}, errors={})",
                    expectedAction, success, action, score, verdict.get("error-codes"));
            throw new RecaptchaException();
        }
    }

    /** Google's answer, or null when it could not be asked. */
    private Map<?, ?> siteverify(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        try {
            return client.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException unreachable) {
            log.warn("reCAPTCHA could not be checked, letting the login through: {}", unreachable.getMessage());
            return null;
        }
    }
}
