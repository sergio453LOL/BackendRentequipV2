package com.rentequip.backend.security;

import com.rentequip.backend.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Issues and validates the HMAC-SHA256 tokens. The signing key comes from the environment; the default
 * only exists so the project boots in development and must be overridden in production.
 */
@Service
public class JwtService {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_COMPANY_ID = "companyId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TOKEN_TYPE = "type";
    /** Only on tokens of the one-click demo: a random id per visit, so per-user limits apply per visitor. */
    public static final String CLAIM_DEMO_SESSION = "dsid";

    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration sessionTtl;

    public JwtService(@Value("${rentequip.security.jwt.secret}") String secret,
                      @Value("${rentequip.security.jwt.access-token-minutes}") long accessTokenMinutes,
                      @Value("${rentequip.security.jwt.session-hours}") long sessionHours) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "rentequip.security.jwt.secret must be at least " + MINIMUM_SECRET_BYTES + " bytes long");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
        this.sessionTtl = Duration.ofHours(sessionHours);
    }

    public String generateAccessToken(CompanyUserDetails user) {
        return generateAccessToken(user, null);
    }

    public String generateAccessToken(CompanyUserDetails user, String demoSession) {
        return buildToken(user, ACCESS_TOKEN, Instant.now().plus(accessTokenTtl), demoSession);
    }

    /** The refresh token of a fresh login: the session it opens ends {@code session-hours} from now. */
    public String generateRefreshToken(CompanyUserDetails user) {
        return generateRefreshToken(user, Instant.now().plus(sessionTtl), null);
    }

    /**
     * The refresh token handed out on a refresh keeps the expiry of the session it continues. Renewing
     * it never extends the session, so after {@code session-hours} the user signs in again, however active.
     */
    public String generateRefreshToken(CompanyUserDetails user, Instant sessionExpiresAt) {
        return generateRefreshToken(user, sessionExpiresAt, null);
    }

    /** A refresh token that also carries the demo visit it continues (null outside the demo). */
    public String generateRefreshToken(CompanyUserDetails user, Instant sessionExpiresAt, String demoSession) {
        return buildToken(user, REFRESH_TOKEN, sessionExpiresAt, demoSession);
    }

    /** Expiry of a brand-new session, for tokens issued outside {@link #generateRefreshToken(CompanyUserDetails)}. */
    public Instant newSessionExpiry() {
        return Instant.now().plus(sessionTtl);
    }

    /** The demo visit id of a token, or null for every normal session. */
    public String demoSession(Claims claims) {
        return claims.get(CLAIM_DEMO_SESSION, String.class);
    }

    public long accessTokenSeconds() {
        return accessTokenTtl.toSeconds();
    }

    /**
     * Returns the claims when the signature and the expiry check out, or null when the token is absent,
     * tampered with or expired. Callers treat null as "not authenticated" and let the entry point answer.
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException invalid) {
            return null;
        }
    }

    public boolean isAccessToken(Claims claims) {
        return ACCESS_TOKEN.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return REFRESH_TOKEN.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public CompanyUserDetails toPrincipal(Claims claims) {
        return new CompanyUserDetails(
                claims.get(CLAIM_USER_ID, Long.class),
                claims.get(CLAIM_EMAIL, String.class),
                null,
                claims.get(CLAIM_COMPANY_ID, Long.class),
                UserRole.valueOf(claims.get(CLAIM_ROLE, String.class)),
                true
        );
    }

    private String buildToken(CompanyUserDetails user, String tokenType, Instant expiresAt, String demoSession) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>(Map.of(
                CLAIM_USER_ID, user.userId(),
                CLAIM_EMAIL, user.email(),
                CLAIM_COMPANY_ID, user.companyId(),
                CLAIM_ROLE, user.role().name(),
                CLAIM_TOKEN_TYPE, tokenType));
        if (demoSession != null) {
            claims.put(CLAIM_DEMO_SESSION, demoSession);
        }
        return Jwts.builder()
                .subject(user.email())
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }
}
