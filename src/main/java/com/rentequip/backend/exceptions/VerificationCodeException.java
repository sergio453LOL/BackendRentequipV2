package com.rentequip.backend.exceptions;

import org.springframework.http.HttpStatus;

/**
 * A verification code was wrong, expired, already used or asked for too often. The message is written
 * for the end user, who reads it under the code field.
 */
public class VerificationCodeException extends RentEquipException {

    private VerificationCodeException(String message, HttpStatus status, String errorCode) {
        super(message, status, errorCode);
    }

    /**
     * The same failure answered with 400 instead of 401, for codes checked inside a signed-in session (profile
     * changes): a 401 there would make the web think the session expired and sign the user out.
     */
    public static VerificationCodeException inSession(VerificationCodeException failure) {
        return failure.getStatus() == HttpStatus.UNAUTHORIZED
                ? new VerificationCodeException(failure.getMessage(), HttpStatus.BAD_REQUEST, failure.getErrorCode())
                : failure;
    }

    public static VerificationCodeException invalid(int attemptsLeft) {
        return new VerificationCodeException(attemptsLeft > 0
                ? "El código no es correcto. Te quedan " + attemptsLeft + (attemptsLeft == 1 ? " intento." : " intentos.")
                : "El código no es correcto y se agotaron los intentos. Pide un código nuevo.",
                HttpStatus.UNAUTHORIZED, "INVALID_VERIFICATION_CODE");
    }

    public static VerificationCodeException expired() {
        return new VerificationCodeException("El código venció o ya se usó. Pide un código nuevo.",
                HttpStatus.UNAUTHORIZED, "VERIFICATION_CODE_EXPIRED");
    }

    public static VerificationCodeException tooSoon(long seconds) {
        return new VerificationCodeException("Ya te enviamos un código. Espera " + seconds + " segundos para pedir otro.",
                HttpStatus.TOO_MANY_REQUESTS, "VERIFICATION_CODE_TOO_SOON");
    }

    /** Turning the protection off asks for the password. 400, not 401: a 401 would close the web session. */
    public static VerificationCodeException wrongPassword() {
        return new VerificationCodeException("La contraseña no es correcta.", HttpStatus.BAD_REQUEST, "TWO_FACTOR_WRONG_PASSWORD");
    }

    public static VerificationCodeException phoneRequired() {
        return new VerificationCodeException("Registra tu celular en el perfil para recibir el código por SMS.",
                HttpStatus.BAD_REQUEST, "TWO_FACTOR_PHONE_REQUIRED");
    }
}
