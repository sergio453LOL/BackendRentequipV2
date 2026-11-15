package com.rentequip.backend.entities;

import com.rentequip.backend.enums.TwoFactorChannel;
import com.rentequip.backend.enums.VerificationPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A one-time code sent by email or SMS. The client only knows the random challenge id, never the row id,
 * and the code itself is stored hashed, like a password.
 */
@Entity
@Table(name = "verification_codes", indexes = {
        @Index(name = "idx_verification_codes_challenge", columnList = "challenge_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class VerificationCode extends BaseEntity {

    @Column(name = "challenge_id", nullable = false, unique = true, length = 36)
    private String challengeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VerificationPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TwoFactorChannel channel;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean used;
}
