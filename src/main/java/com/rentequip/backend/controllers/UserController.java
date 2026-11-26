package com.rentequip.backend.controllers;

import com.rentequip.backend.dtos.request.PasswordChangeRequest;
import com.rentequip.backend.dtos.request.ProfileUpdateRequest;
import com.rentequip.backend.dtos.request.TwoFactorDisableRequest;
import com.rentequip.backend.dtos.request.TwoFactorSetupRequest;
import com.rentequip.backend.dtos.request.TwoFactorVerifyRequest;
import com.rentequip.backend.dtos.response.TwoFactorChallengeResponse;
import com.rentequip.backend.dtos.request.UserCreateRequest;
import com.rentequip.backend.dtos.request.UserUpdateRequest;
import com.rentequip.backend.dtos.response.UserResponse;
import com.rentequip.backend.services.TwoFactorService;
import com.rentequip.backend.services.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;
    private final TwoFactorService twoFactorService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request,
                                                UriComponentsBuilder uriBuilder) {
        UserResponse created = userService.create(request);
        URI location = uriBuilder.path("/api/v1/users/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** The signed-in user's own profile, for any role. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> findMe() {
        return ResponseEntity.ok(userService.findMe());
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateMe(request));
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody PasswordChangeRequest request) {
        userService.changeMyPassword(request);
        return ResponseEntity.noContent().build();
    }

    /** Step 1 of turning on the two-step verification: a code goes to the chosen channel. */
    @PostMapping("/me/two-factor/setup")
    public ResponseEntity<TwoFactorChallengeResponse> startTwoFactorSetup(@Valid @RequestBody TwoFactorSetupRequest request) {
        return ResponseEntity.ok(twoFactorService.startSetup(request));
    }

    /** Step 2: the code proves the channel works, and the protection is on from the next login. */
    @PostMapping("/me/two-factor/confirm")
    public ResponseEntity<UserResponse> confirmTwoFactor(@Valid @RequestBody TwoFactorVerifyRequest request) {
        return ResponseEntity.ok(twoFactorService.confirmSetup(request));
    }

    /**
     * For an account without a password (created with Google): a code to its email that confirms setting
     * a first password or turning off the two-step verification.
     */
    @PostMapping("/me/account-code")
    public ResponseEntity<TwoFactorChallengeResponse> sendAccountCode() {
        return ResponseEntity.ok(twoFactorService.startAccountCode());
    }

    @PostMapping("/me/two-factor/disable")
    public ResponseEntity<UserResponse> disableTwoFactor(@Valid @RequestBody TwoFactorDisableRequest request) {
        return ResponseEntity.ok(twoFactorService.disable(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        userService.disable(id);
        return ResponseEntity.noContent().build();
    }
}
