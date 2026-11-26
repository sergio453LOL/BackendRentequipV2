package com.rentequip.backend.mappers;

import static com.rentequip.backend.mappers.MappingUtils.applyIfPresent;
import static com.rentequip.backend.mappers.MappingUtils.blankToNull;

import com.rentequip.backend.dtos.request.UserCreateRequest;
import com.rentequip.backend.dtos.request.ProfileUpdateRequest;
import com.rentequip.backend.dtos.request.UserUpdateRequest;
import com.rentequip.backend.dtos.response.UserResponse;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    /**
     * The password must already be encoded (BCrypt) by the service layer.
     */
    public User toEntity(UserCreateRequest request, Company company, String encodedPassword) {
        User user = new User();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPassword(encodedPassword);
        user.setPhone(request.phone());
        user.setRole(request.role());
        user.setEnabled(true);
        user.setCompany(company);
        return user;
    }

    public void updateEntity(User user, UserUpdateRequest request) {
        applyIfPresent(request.firstName(), value -> user.setFirstName(value.trim()));
        applyIfPresent(request.lastName(), value -> user.setLastName(value.trim()));
        applyIfPresent(request.phone(), user::setPhone);
        applyIfPresent(request.role(), user::setRole);
    }

    public void applyProfile(User user, ProfileUpdateRequest request) {
        applyIfPresent(request.firstName(), value -> user.setFirstName(value.trim()));
        applyIfPresent(request.lastName(), value -> user.setLastName(value.trim()));
        applyIfPresent(request.phone(), value -> user.setPhone(blankToNull(value)));
        applyIfPresent(request.avatarUrl(), value -> user.setAvatarUrl(blankToNull(value)));
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getRole(),
                user.isEnabled(),
                user.getCompany().getId(),
                user.getCompany().getName(),
                user.getCreatedAt(),
                user.getTwoFactorChannel(),
                user.hasPassword()
        );
    }
}
