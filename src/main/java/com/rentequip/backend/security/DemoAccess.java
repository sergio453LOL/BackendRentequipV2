package com.rentequip.backend.security;

import com.rentequip.backend.exceptions.DemoAccountLockedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * The one-click demo: visitors (the jury, the public at the Demo Day) enter as the demo renter configured
 * in {@code DEMO_LOGIN_EMAIL} without knowing its password. Because anybody can be that user, nothing that
 * would lock the next visitor out may change: the password, the two-step verification, the profile, the
 * company or its users. With the property empty the demo entrance is closed.
 */
@Component
public class DemoAccess {

    private final String email;

    public DemoAccess(@Value("${rentequip.demo-login.email:}") String email) {
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isEnabled() {
        return !email.isEmpty();
    }

    /** Address of the shared demo account; only meaningful when {@link #isEnabled()}. */
    public String email() {
        return email;
    }

    public boolean isDemoAccount(String candidate) {
        return isEnabled() && candidate != null && email.equals(candidate.trim().toLowerCase(Locale.ROOT));
    }

    /** Refuses account and company changes while signed in as the shared demo account. */
    public void forbidChangesBy(CompanyUserDetails principal) {
        if (principal != null && isDemoAccount(principal.email())) {
            throw new DemoAccountLockedException();
        }
    }
}
