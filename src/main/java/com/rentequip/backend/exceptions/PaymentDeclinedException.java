package com.rentequip.backend.exceptions;

import org.springframework.http.HttpStatus;

/**
 * The payment provider refused the card. 402 tells the client the request was well formed but has to be
 * retried with another payment method.
 */
public class PaymentDeclinedException extends RentEquipException {

    public PaymentDeclinedException(String message) {
        super(message, HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED");
    }
}
