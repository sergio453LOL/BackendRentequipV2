package com.rentequip.backend.entities;

import com.rentequip.backend.enums.TwoFactorChannel;
import com.rentequip.backend.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_company", columnList = "company_id")
})
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @NotBlank
    @Size(max = 80)
    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @NotBlank
    @Size(max = 80)
    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 150)
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank
    @Size(min = 8, max = 255)
    @Column(nullable = false, length = 255)
    private String password;

    /**
     * False for accounts created with Google, whose password is a random one nobody knows: they set their
     * first password (or turn off the two-step verification) with a code sent to their email instead of the
     * current password. Nullable so the column can be added to an existing table; null means true.
     */
    @Column(name = "password_set")
    private Boolean passwordSet = Boolean.TRUE;

    @Size(max = 20)
    @Column(length = 20)
    private String phone;

    /** Public URL of the profile photo; null shows the initials. */
    @Size(max = 500)
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.OPERATOR;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Where the sign-in code goes; null means the two-step verification is off. */
    @Enumerated(EnumType.STRING)
    @Column(name = "two_factor_channel", length = 10)
    private TwoFactorChannel twoFactorChannel;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Whether the user knows their password (see {@link #passwordSet}). */
    public boolean hasPassword() {
        return !Boolean.FALSE.equals(passwordSet);
    }

    public void setPasswordSet(boolean passwordSet) {
        this.passwordSet = passwordSet;
    }
}
