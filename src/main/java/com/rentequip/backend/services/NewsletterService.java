package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.NewsletterSubscriptionRequest;
import com.rentequip.backend.dtos.response.NewsletterSubscriptionResponse;
import com.rentequip.backend.entities.NewsletterSubscription;
import com.rentequip.backend.repositories.NewsletterSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Offers sign-up from the landing page. Signing up twice is not an error: the visitor just sees that they are
 * already in. The welcome email goes out only on the first sign-up, so the public form cannot be used to send
 * the same address mail again and again.
 */
@Service
@RequiredArgsConstructor
public class NewsletterService {

    private final NewsletterSubscriptionRepository repository;
    private final EmailService emailService;

    @Transactional
    public NewsletterSubscriptionResponse subscribe(NewsletterSubscriptionRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailIgnoreCase(email)) {
            return new NewsletterSubscriptionResponse(email, true);
        }
        NewsletterSubscription subscription = new NewsletterSubscription();
        subscription.setEmail(email);
        subscription.setConsentAt(LocalDateTime.now());
        repository.save(subscription);
        emailService.send(email, "Ya estás en la lista de ofertas de RentEquip", "newsletter-welcome", Map.of());
        return new NewsletterSubscriptionResponse(email, false);
    }
}
