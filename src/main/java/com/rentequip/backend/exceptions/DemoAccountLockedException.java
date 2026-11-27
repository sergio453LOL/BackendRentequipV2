package com.rentequip.backend.exceptions;

import org.springframework.http.HttpStatus;

/**
 * The shared demo account tried to change something that would lock the next visitor out (password,
 * two-step verification, profile, company or its users). Maps to 403 with code DEMO_ACCOUNT_LOCKED.
 */
public class DemoAccountLockedException extends RentEquipException {

    public DemoAccountLockedException() {
        super("The demo account cannot change its account or company settings",
                HttpStatus.FORBIDDEN, "DEMO_ACCOUNT_LOCKED");
    }
}
