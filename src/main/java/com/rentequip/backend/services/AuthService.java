package com.rentequip.backend.services;

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
import com.rentequip.backend.exceptions.UnauthorizedException;
import com.rentequip.backend.repositories.UserRepository;
import com.rentequip.backend.security.CompanyUserDetails;
import com.rentequip.backend.security.CustomUserDetailsService;
import com.rentequip.backend.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login and refresh. The only place in the application that mints tokens.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final CompanyService companyService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TwoFactorService twoFactorService;
    private final CompanyVerificationService companyVerificationService;

    /** A login ends either with the tokens or, when the two-step verification is on, with a code sent. */
    public record LoginOutcome(AuthResponse tokens, TwoFactorChallengeResponse challenge) {
    }

    /**
     * Creates the company and its first administrator atomically: if the user cannot be persisted the
     * company must not survive, or the tenant would be left with nobody able to manage it.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw DuplicateResourceException.of("User", "email", request.email());
        }
        Company company = companyService.createEntity(request.company());
        companyVerificationService.verifyQuietly(company);
        User administrator = userRepository.save(buildAdministrator(request, company));
        return issueTokens(CompanyUserDetails.from(administrator), company.getName());
    }

    @Transactional
    public LoginOutcome login(LoginRequest request) {
        CompanyUserDetails principal = authenticate(request);
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (user.getTwoFactorChannel() != null) {
            return new LoginOutcome(null, twoFactorService.startLogin(user));
        }
        return new LoginOutcome(issueTokens(principal, companyNameOf(principal)), null);
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
        String refreshToken = jwtService.generateRefreshToken(principal, claims.getExpiration().toInstant());
        return issueTokens(principal, companyNameOf(principal), refreshToken);
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

    private User buildAdministrator(RegisterRequest request, Company company) {
        User administrator = new User();
        administrator.setFirstName(request.firstName().trim());
        administrator.setLastName(request.lastName().trim());
        administrator.setEmail(request.email().trim().toLowerCase());
        administrator.setPassword(passwordEncoder.encode(request.password()));
        administrator.setPhone(request.phone());
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
        return new AuthResponse(
                jwtService.generateAccessToken(principal),
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
