package com.rentequip.backend.exceptions;

import org.springframework.http.HttpStatus;

/**
 * A message the user asked for explicitly (e.g. "send me the receipt") could not be delivered. Unlike the
 * automatic notifications, this failure is reported back so the user knows nothing arrived.
 */
public class NotificationDeliveryException extends RentEquipException {

    public NotificationDeliveryException(HttpStatus status, String errorCode, String message) {
        super(message, status, errorCode);
    }

    /** The channel has no credentials in this environment (e.g. no SMTP or Twilio keys). */
    public static NotificationDeliveryException notConfigured(String channel) {
        return new NotificationDeliveryException(HttpStatus.SERVICE_UNAVAILABLE, "CHANNEL_NOT_CONFIGURED",
                "El envío por " + channel + " no está configurado en este servidor.");
    }

    /** The provider refused or failed the send; the message is already written for the end user. */
    public static NotificationDeliveryException failed(String message) {
        return new NotificationDeliveryException(HttpStatus.BAD_GATEWAY, "DELIVERY_FAILED", message);
    }
}
