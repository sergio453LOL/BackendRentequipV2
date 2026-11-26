package com.rentequip.backend.security;

import com.rentequip.backend.exceptions.GoogleSignInException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Checks the ID token that Google Identity Services hands the web after "Continuar con Google": signed with
 * Google's published keys, issued by Google, meant for this app's client id and not expired. Only then is
 * its email trusted to pick the RentEquip account.
 */
@Component
public class GoogleIdTokenVerifier {

    static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    /** What the app takes from a checked Google token. The names may be blank: not every account has them. */
    public record GoogleIdentity(String email, String firstName, String lastName) {
    }

    private final String clientId;
    private volatile JwtDecoder decoder;

    public GoogleIdTokenVerifier(@Value("${rentequip.security.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    public GoogleIdentity verify(String credential) {
        if (clientId.isEmpty()) {
            throw GoogleSignInException.notConfigured();
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(credential);
        } catch (BadJwtException invalid) {
            throw GoogleSignInException.invalidToken();
        } catch (JwtException unreachable) {
            // Anything that is not a verdict on the token itself: Google's keys could not be fetched.
            throw GoogleSignInException.unavailable();
        }
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw GoogleSignInException.unverifiedEmail();
        }
        return new GoogleIdentity(
                email.trim().toLowerCase(Locale.ROOT),
                blankToEmpty(jwt.getClaimAsString("given_name")),
                blankToEmpty(jwt.getClaimAsString("family_name")));
    }

    /** Built on first use: the keys are fetched from Google then, and cached and rotated by Nimbus. */
    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
                    OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                            new JwtTimestampValidator(),
                            new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                                    issuer -> issuer != null && ISSUERS.contains(issuer.toString())),
                            new JwtClaimValidator<Object>(JwtClaimNames.AUD,
                                    audience -> audience instanceof Collection<?> values && values.contains(clientId)));
                    nimbus.setJwtValidator(validator);
                    decoder = nimbus;
                }
                current = decoder;
            }
        }
        return current;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
