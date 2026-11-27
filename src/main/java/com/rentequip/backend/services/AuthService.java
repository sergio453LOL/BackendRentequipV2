package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.CompanyCreateRequest;
import com.rentequip.backend.dtos.request.GoogleLoginRequest;
import com.rentequip.backend.dtos.request.GoogleRegisterRequest;
import com.rentequip.backend.dtos.request.LoginRequest;
import com.rentequip.backend.dtos.request.RefreshTokenRequest;
import com.rentequip.backend.dtos.request.RegisterRequest;
import com.rentequip.backend.dtos.request.TwoFactorVerifyRequest;
import com.rentequip.backend.dtos.response.AuthResponse;
import com.rentequip.backend.dtos.response.TwoFactorChallengeResponse;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.User;
import com.rentequip.backend.enums.UserRole;
import com.rentequip.backend.exceptions.DuplicateResourceException;
import com.rentequip.backend.exceptions.GoogleSignInException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.exceptions.UnauthorizedException;
import com.rentequip.backend.repositories.UserRepository;
import com.rentequip.backend.security.AbuseGuard;
import com.rentequip.backend.security.CompanyUserDetails;
import com.rentequip.backend.security.CustomUserDetailsService;
import com.rentequip.backend.security.DemoAccess;
import com.rentequip.backend.security.GoogleIdTokenVerifier;
import com.rentequip.backend.security.GoogleIdTokenVerifier.GoogleIdentity;
import com.rentequip.backend.security.JwtService;
import com.rentequip.backend.security.RecaptchaVerifier;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Registration, login and refresh. The only place in the application that mints tokens.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final CompanyService companyService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TwoFactorService twoFactorService;
    private final CompanyVerificationService companyVerificationService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final RecaptchaVerifier recaptchaVerifier;
    private final AbuseGuard abuseGuard;
    private final DemoAccess demoAccess;

    /** A login ends either with the tokens or, when the two-step verification is on, with a code sent. */
    public record LoginOutcome(AuthResponse tokens, TwoFactorChallengeResponse challenge) {
    }

    /**
     * Creates the company and its first administrator atomically: if the user cannot be persisted the
     * company must not survive, or the tenant would be left with nobody able to manage it.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        abuseGuard.beforeRegister();
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw DuplicateResourceException.of("User", "email", request.email());
        }
        return createTenant(request.company(), request.firstName(), request.lastName(), request.email(),
                request.password(), true, request.phone());
    }

    /**
     * "Continuar con Google" on the login page. The email Google verified picks the account; an account with
     * the two-step verification on still gets its code, as with the password.
     */
    @Transactional
    public LoginOutcome loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdentity identity = googleIdTokenVerifier.verify(request.credential());
        User user = userRepository.findByEmailIgnoreCase(identity.email())
                .orElseThrow(GoogleSignInException::notRegistered);
        CompanyUserDetails principal = CompanyUserDetails.from(user);
        if (!principal.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }
        if (user.getTwoFactorChannel() != null) {
            return new LoginOutcome(null, twoFactorService.startLogin(user));
        }
        return new LoginOutcome(issueTokens(principal, companyNameOf(principal)), null);
    }

    /**
     * Sign-up after a Google login found no account. The email comes from the checked credential, never from
     * the form, and the password is a random one nobody knows: this administrator signs in with Google, and
     * can set a real password later from the profile with a code sent to that email.
     */
    @Transactional
    public AuthResponse registerWithGoogle(GoogleRegisterRequest request) {
        abuseGuard.beforeRegister();
        GoogleIdentity identity = googleIdTokenVerifier.verify(request.credential());
        if (userRepository.existsByEmailIgnoreCase(identity.email())) {
            throw DuplicateResourceException.of("User", "email", identity.email());
        }
        // 32 random bytes: under the 72 bytes BCrypt accepts, and far beyond guessing.
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String unusablePassword = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return createTenant(request.company(), request.firstName(), request.lastName(), identity.email(),
                unusablePassword, false, request.phone());
    }

    private AuthResponse createTenant(CompanyCreateRequest companyRequest, String firstName, String lastName,
                                      String email, String rawPassword, boolean passwordSet, String phone) {
        Company company = companyService.createEntity(companyRequest);
        companyVerificationService.verifyQuietly(company);
        User administrator = userRepository.save(
                buildAdministrator(firstName, lastName, email, rawPassword, passwordSet, phone, company));
        return issueTokens(CompanyUserDetails.from(administrator), company.getName());
    }

    @Transactional
    public LoginOutcome login(LoginRequest request) {
        // Before the password: a bot guessing passwords is turned away without learning anything.
        recaptchaVerifier.check(request.recaptchaToken(), "login");
        // reCAPTCHA cannot be required (the mobile app has none), so failures per email and per IP are
        // what really stops password guessing.
        abuseGuard.beforeLogin(request.email());
        CompanyUserDetails principal;
        try {
            principal = authenticate(request);
        } catch (UnauthorizedException failed) {
            abuseGuard.loginFailed(request.email());
            throw failed;
        }
        abuseGuard.loginSucceeded(request.email());
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (user.getTwoFactorChannel() != null) {
            return new LoginOutcome(null, twoFactorService.startLogin(user));
        }
        return new LoginOutcome(issueTokens(principal, companyNameOf(principal)), null);
    }

    /**
     * One-click entrance as the shared demo renter (DEMO_LOGIN_EMAIL), for visitors who will not register a
     * company to try the product. No password travels: the account is fixed on the server, and DemoAccess
     * keeps it from changing anything that would lock the next visitor out. Closed (404) when not set.
     */
    @Transactional(readOnly = true)
    public AuthResponse loginAsDemo() {
        if (!demoAccess.isEnabled()) {
            throw new ResourceNotFoundException("The demo entrance is not enabled");
        }
        CompanyUserDetails principal;
        try {
            principal = userDetailsService.loadUserByUsername(demoAccess.email());
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException missing) {
            throw new ResourceNotFoundException("The demo account does not exist");
        }
        if (!principal.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }
        // A random id per visit: the assistant's per-user limits then apply to each visitor, not to all of them.
        String demoSession = java.util.UUID.randomUUID().toString();
        return issueTokens(principal, companyNameOf(principal),
                jwtService.generateRefreshToken(principal, jwtService.newSessionExpiry(), demoSession), demoSession);
    }

    /**
     * Second step of a protected login. Not transactional on purpose: a wrong code must still count as an
     * attempt, and an outer transaction would roll that back together with the failure.
     */
    public AuthResponse verifyLogin(TwoFactorVerifyRequest request) {
        User user = twoFactorService.verifyLogin(request);
        CompanyUserDetails principal = userDetailsService.loadUserByUsername(user.getEmail());
        if (!principal.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }
        return issueTokens(principal, companyNameOf(principal));
    }

    /**
     * Exchanges a refresh token for a new pair. The user is reloaded on purpose: a role change or a
     * disabled account must take effect on the next refresh instead of living on until the token expires.
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        Claims claims = jwtService.parse(request.refreshToken());
        if (claims == null || !jwtService.isRefreshToken(claims)) {
            throw new UnauthorizedException("The refresh token is invalid or has expired");
        }
        CompanyUserDetails principal = userDetailsService.loadUserByUsername(
                claims.get(JwtService.CLAIM_EMAIL, String.class));
        if (!principal.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }
        // The new refresh token inherits the session's expiry, so a session lasts a fixed time from login.
        String demoSession = jwtService.demoSession(claims);
        String refreshToken = jwtService.generateRefreshToken(principal, claims.getExpiration().toInstant(), demoSession);
        return issueTokens(principal, companyNameOf(principal), refreshToken, demoSession);
    }

    private CompanyUserDetails authenticate(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
            return (CompanyUserDetails) authentication.getPrincipal();
        } catch (AuthenticationException failed) {
            throw new UnauthorizedException("Invalid email or password");
        }
    }

    private User buildAdministrator(String firstName, String lastName, String email, String rawPassword,
                                    boolean passwordSet, String phone, Company company) {
        User administrator = new User();
        administrator.setFirstName(firstName.trim());
        administrator.setLastName(lastName.trim());
        administrator.setEmail(email.trim().toLowerCase());
        administrator.setPassword(passwordEncoder.encode(rawPassword));
        administrator.setPasswordSet(passwordSet);
        administrator.setPhone(phone);
        administrator.setRole(UserRole.ADMIN);
        administrator.setEnabled(true);
        administrator.setCompany(company);
        return administrator;
    }

    /** The web keeps the company name in the session to greet the user, so login and refresh return it too. */
    private String companyNameOf(CompanyUserDetails principal) {
        return companyService.findById(principal.companyId()).name();
    }

    private AuthResponse issueTokens(CompanyUserDetails principal, String companyName) {
        return issueTokens(principal, companyName, jwtService.generateRefreshToken(principal));
    }

    private AuthResponse issueTokens(CompanyUserDetails principal, String companyName, String refreshToken) {
        return issueTokens(principal, companyName, refreshToken, null);
    }

    private AuthResponse issueTokens(CompanyUserDetails principal, String companyName, String refreshToken,
                                     String demoSession) {
        return new AuthResponse(
                jwtService.generateAccessToken(principal, demoSession),
                refreshToken,
                AuthResponse.BEARER,
                jwtService.accessTokenSeconds(),
                principal.userId(),
                principal.email(),
                principal.role(),
                principal.companyId(),
                companyName
        );
    }
}
