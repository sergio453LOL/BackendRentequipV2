package com.rentequip.backend.exceptions;

import org.springframework.http.HttpStatus;

/**
 * reCAPTCHA judged the login to come from a bot (or no token came when one is required). The message is
 * written for the person on the other side, who most likely is not a bot and only needs to try again.
 */
public class RecaptchaException extends RentEquipException {

    public RecaptchaException() {
        super("No pudimos verificar que eres una persona. Recarga la página e inténtalo otra vez.",
                HttpStatus.BAD_REQUEST, "RECAPTCHA_FAILED");
    }
}
