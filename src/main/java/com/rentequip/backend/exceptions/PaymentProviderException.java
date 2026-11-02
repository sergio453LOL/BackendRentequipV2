package com.rentequip.backend.exceptions;

import org.springframework.http.HttpStatus;

/**
 * The payment provider could not be reached or rejected our request for a reason that is not the card's
 * fault (network error, bad configuration). 502 tells the client the problem is upstream, not in its data.
 */
public class PaymentProviderException extends RentEquipException {

    public PaymentProviderException(String message) {
        super(message, HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR");
    }
}
