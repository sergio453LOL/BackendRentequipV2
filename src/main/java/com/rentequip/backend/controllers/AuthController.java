package com.rentequip.backend.controllers;

import com.rentequip.backend.dtos.request.GoogleLoginRequest;
import com.rentequip.backend.dtos.request.GoogleRegisterRequest;
import com.rentequip.backend.dtos.request.LoginRequest;
import com.rentequip.backend.dtos.request.RefreshTokenRequest;
import com.rentequip.backend.dtos.request.RegisterRequest;
import com.rentequip.backend.dtos.request.TwoFactorVerifyRequest;
import com.rentequip.backend.dtos.response.AuthResponse;
import com.rentequip.backend.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * 200 with the tokens, or 202 with a {@code TwoFactorChallengeResponse} when the account has the
     * two-step verification on: the code is on its way and {@code /login/verify} finishes the login.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginOutcome outcome = authService.login(request);
        return outcome.challenge() != null
                ? ResponseEntity.status(HttpStatus.ACCEPTED).body(outcome.challenge())
                : ResponseEntity.ok(outcome.tokens());
    }

    @PostMapping("/login/verify")
    public ResponseEntity<AuthResponse> verifyLogin(@Valid @RequestBody TwoFactorVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyLogin(request));
    }

    /**
     * Same answers as {@code /login} (200 tokens, 202 two-step code), plus 404 {@code GOOGLE_ACCOUNT_NOT_REGISTERED}
     * when no account has that Google email yet: the web then opens the sign-up with Google.
     */
    @PostMapping("/google")
    public ResponseEntity<?> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        AuthService.LoginOutcome outcome = authService.loginWithGoogle(request);
        return outcome.challenge() != null
                ? ResponseEntity.status(HttpStatus.ACCEPTED).body(outcome.challenge())
                : ResponseEntity.ok(outcome.tokens());
    }

    @PostMapping("/google/register")
    public ResponseEntity<AuthResponse> registerWithGoogle(@Valid @RequestBody GoogleRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerWithGoogle(request));
    }

    /** One-click demo: tokens of the shared demo renter, or 404 when the demo entrance is closed. */
    @PostMapping("/demo")
    public ResponseEntity<AuthResponse> loginAsDemo() {
        return ResponseEntity.ok(authService.loginAsDemo());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }
}
