package com.rentequip.backend.controllers;

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

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }
}
