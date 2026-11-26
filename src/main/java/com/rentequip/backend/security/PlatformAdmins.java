package com.rentequip.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Operators of the marketplace itself, not of one company. Every company that signs up gets an ADMIN of its
 * own, so ADMIN alone cannot guard what all tenants share, such as the equipment categories. Those are
 * managed only by the emails listed in {@code PLATFORM_ADMIN_EMAILS} (comma separated); with the list empty
 * nobody can change them through the API and the catalogue keeps the categories the seeders load.
 *
 * <p>Used from {@code @PreAuthorize("@platformAdmins.includes(authentication)")}.</p>
 */
@Component("platformAdmins")
public class PlatformAdmins {

    private final Set<String> emails;

    public PlatformAdmins(@Value("${rentequip.platform.admin-emails:}") List<String> emails) {
        this.emails = emails.stream()
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean includes(Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof CompanyUserDetails principal
                && principal.email() != null
                && emails.contains(principal.email().toLowerCase(Locale.ROOT));
    }
}
