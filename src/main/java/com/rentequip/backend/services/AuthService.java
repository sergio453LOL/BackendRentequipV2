package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.LoginRequest;
import com.rentequip.backend.dtos.request.RefreshTokenRequest;
import com.rentequip.backend.dtos.request.RegisterRequest;
import com.rentequip.backend.dtos.response.AuthResponse;
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
        User administrator = userRepository.save(buildAdministrator(request, company));
        return issueTokens(CompanyUserDetails.from(administrator), company.getName());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        CompanyUserDetails principal = authenticate(request);
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
        return issueTokens(principal, companyNameOf(principal));
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
        return new AuthResponse(
                jwtService.generateAccessToken(principal),
                jwtService.generateRefreshToken(principal),
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
