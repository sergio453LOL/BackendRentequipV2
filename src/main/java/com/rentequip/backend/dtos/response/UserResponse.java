package com.rentequip.backend.dtos.response;

import com.rentequip.backend.enums.TwoFactorChannel;
import com.rentequip.backend.enums.UserRole;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String avatarUrl,
        UserRole role,
        boolean enabled,
        Long companyId,
        String companyName,
        LocalDateTime createdAt,
        /** Null when the two-step verification is off. */
        TwoFactorChannel twoFactorChannel,
        /** False for an account created with Google that never set a password: it confirms changes by email code. */
        boolean passwordSet
) {
}
