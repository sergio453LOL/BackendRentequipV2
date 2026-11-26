package com.rentequip.backend.exceptions;

import org.springframework.http.HttpStatus;

/**
 * "Continuar con Google" could not finish. Each case has its own code so the web can explain it, and one of
 * them is not a failure at all: {@code GOOGLE_ACCOUNT_NOT_REGISTERED} sends the visitor to the sign-up form.
 */
public class GoogleSignInException extends RentEquipException {

    private GoogleSignInException(HttpStatus status, String errorCode, String message) {
        super(message, status, errorCode);
    }

    /** No Google client id on this server, so no Google token can be checked. */
    public static GoogleSignInException notConfigured() {
        return new GoogleSignInException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NOT_CONFIGURED",
                "Google sign-in is not configured on this server");
    }

    /** Bad signature, expired, issued for another app or by someone other than Google. */
    public static GoogleSignInException invalidToken() {
        return new GoogleSignInException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN",
                "The Google sign-in is invalid or has expired");
    }

    /** Without a verified email there is nothing to match the account by. */
    public static GoogleSignInException unverifiedEmail() {
        return new GoogleSignInException(HttpStatus.UNAUTHORIZED, "GOOGLE_EMAIL_NOT_VERIFIED",
                "The Google account has no verified email");
    }

    /** Google's public keys could not be fetched, so the token could not be checked either way. */
    public static GoogleSignInException unavailable() {
        return new GoogleSignInException(HttpStatus.BAD_GATEWAY, "GOOGLE_UNAVAILABLE",
                "Google could not be reached to check the sign-in");
    }

    /** The Google account is fine but no RentEquip user has its email yet: the company must be registered. */
    public static GoogleSignInException notRegistered() {
        return new GoogleSignInException(HttpStatus.NOT_FOUND, "GOOGLE_ACCOUNT_NOT_REGISTERED",
                "No account uses this Google email yet");
    }
}
