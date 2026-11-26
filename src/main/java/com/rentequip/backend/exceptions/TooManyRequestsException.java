package com.rentequip.backend.exceptions;

import org.springframework.http.HttpStatus;

/**
 * A caller went over one of the rate limits (login attempts, identity lookups, receipt sends...). Answered
 * with 429 and a Retry-After header carrying {@link #getRetryAfterSeconds()}.
 */
public class TooManyRequestsException extends RentEquipException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("Demasiados intentos. Vuelve a intentarlo en " + describe(retryAfterSeconds) + ".",
                HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static String describe(long seconds) {
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " segundo" : " segundos");
        }
        long minutes = (seconds + 59) / 60;
        return minutes + (minutes == 1 ? " minuto" : " minutos");
    }
}
