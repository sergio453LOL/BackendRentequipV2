package com.rentequip.backend.exceptions;

import org.springframework.http.HttpStatus;

/**
 * A password change the rules refuse. Each case has its own code so the client can point at the field
 * that caused it.
 */
public class PasswordChangeException extends RentEquipException {

    private PasswordChangeException(String message, String errorCode) {
        super(message, HttpStatus.BAD_REQUEST, errorCode);
    }

    public static PasswordChangeException wrongCurrentPassword() {
        return new PasswordChangeException("The current password is incorrect", "WRONG_CURRENT_PASSWORD");
    }

    public static PasswordChangeException sameAsCurrent() {
        return new PasswordChangeException("The new password must be different from the current one", "SAME_PASSWORD");
    }
}
