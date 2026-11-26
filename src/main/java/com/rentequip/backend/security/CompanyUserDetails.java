package com.rentequip.backend.security;

import com.rentequip.backend.entities.User;
import com.rentequip.backend.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Principal stored in the SecurityContext. Carries the company the user acts for, which is what every
 * ownership check in the service layer needs; without it each request would have to hit the database
 * again just to resolve the tenant.
 */
public record CompanyUserDetails(
        Long userId,
        String email,
        String passwordHash,
        Long companyId,
        UserRole role,
        boolean active
) implements UserDetails {

    public static final String ROLE_PREFIX = "ROLE_";

    public static CompanyUserDetails from(User user) {
        return new CompanyUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getCompany().getId(),
                user.getRole(),
                // A deactivated company takes its users with it: none of them can sign in or refresh.
                user.isEnabled() && user.getCompany().isActive()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
