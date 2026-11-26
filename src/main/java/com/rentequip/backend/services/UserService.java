package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.PasswordChangeRequest;
import com.rentequip.backend.dtos.request.ProfileUpdateRequest;
import com.rentequip.backend.dtos.request.UserCreateRequest;
import com.rentequip.backend.dtos.request.UserUpdateRequest;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.dtos.response.UserResponse;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.User;
import com.rentequip.backend.enums.UserRole;
import com.rentequip.backend.exceptions.DuplicateResourceException;
import com.rentequip.backend.exceptions.ForbiddenOperationException;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.PasswordChangeException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.mappers.UserMapper;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.repositories.UserRepository;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final CompanyRepository companyRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        validateEmailIsFree(request.email(), null);
        validateBelongsToCallerCompany(request.companyId());
        Company company = findCompanyOrThrow(request.companyId());
        User user = userMapper.toEntity(request, company, passwordEncoder.encode(request.password()));
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long userId, UserUpdateRequest request) {
        User user = findOrThrow(userId);
        validateSameCompany(user, currentUser.requireCompanyId());
        if (request.role() != null) {
            validateCompanyKeepsAnAdmin(user, request.role());
        }
        userMapper.updateEntity(user, request);
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse findMe() {
        return userMapper.toResponse(findOrThrow(currentUser.requireUserId()));
    }

    /**
     * Self-service profile: the caller edits only their own name, phone and photo, never their role.
     */
    @Transactional
    public UserResponse updateMe(ProfileUpdateRequest request) {
        User user = findOrThrow(currentUser.requireUserId());
        userMapper.applyProfile(user, request);
        return userMapper.toResponse(user);
    }

    @Transactional
    public void changeMyPassword(PasswordChangeRequest request) {
        User user = findOrThrow(currentUser.requireUserId());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw PasswordChangeException.wrongCurrentPassword();
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw PasswordChangeException.sameAsCurrent();
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public void disable(Long userId) {
        User user = findOrThrow(userId);
        validateSameCompany(user, currentUser.requireCompanyId());
        validateCompanyKeepsAnAdmin(user, UserRole.OPERATOR);
        user.setEnabled(false);
    }

    /** A user's profile carries their email and phone, so only people of the same company may read it. */
    @Transactional(readOnly = true)
    public UserResponse findById(Long userId) {
        User user = findOrThrow(userId);
        validateSameCompany(user, currentUser.requireCompanyId());
        return userMapper.toResponse(user);
    }

    /** The staff of a company, with their contact details: visible only from inside that company. */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> findByCompany(Long companyId, UserRole role, Pageable pageable) {
        if (!companyId.equals(currentUser.requireCompanyId())) {
            throw ForbiddenOperationException.notOwner("company", companyId);
        }
        assertCompanyExists(companyId);
        Page<User> page = role == null
                ? userRepository.findByCompanyId(companyId, pageable)
                : userRepository.findByCompanyIdAndRole(companyId, role, pageable);
        return PageResponse.from(page, userMapper::toResponse);
    }

    private User findOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    private Company findCompanyOrThrow(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Company", companyId));
    }

    private void assertCompanyExists(Long companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw ResourceNotFoundException.of("Company", companyId);
        }
    }

    private void validateEmailIsFree(String email, Long currentId) {
        boolean taken = currentId == null
                ? userRepository.existsByEmailIgnoreCase(email)
                : userRepository.existsByEmailIgnoreCaseAndIdNot(email, currentId);
        if (taken) {
            throw DuplicateResourceException.of("User", "email", email);
        }
    }

    /**
     * A company can only create users inside itself, whatever company id the payload carries.
     */
    private void validateBelongsToCallerCompany(Long companyId) {
        if (!companyId.equals(currentUser.requireCompanyId())) {
            throw new ForbiddenOperationException("You can only create users inside your own company");
        }
    }

    private void validateSameCompany(User user, Long actingCompanyId) {
        if (!user.getCompany().getId().equals(actingCompanyId)) {
            throw ForbiddenOperationException.notOwner("user", user.getId());
        }
    }

    /**
     * A company must never be left without an administrator, otherwise nobody can manage its catalogue.
     */
    private void validateCompanyKeepsAnAdmin(User user, UserRole newRole) {
        boolean losingAdminRole = user.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN;
        if (losingAdminRole && userRepository.countByCompanyIdAndRole(user.getCompany().getId(), UserRole.ADMIN) <= 1) {
            throw new InvalidOperationException("The company must keep at least one administrator");
        }
    }
}
