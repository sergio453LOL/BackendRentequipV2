package com.rentequip.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An address that asked, from the landing page, to hear about offers. It keeps when the person gave their
 * consent, since Peru's data protection law (Ley 29733) asks for proof of it before sending advertising.
 */
@Entity
@Table(name = "newsletter_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class NewsletterSubscription extends BaseEntity {

    @NotBlank
    @Email
    @Size(max = 160)
    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @NotNull
    @Column(name = "consent_at", nullable = false)
    private LocalDateTime consentAt;
}
