package com.rentequip.backend.repositories;

import com.rentequip.backend.entities.NewsletterSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsletterSubscriptionRepository extends JpaRepository<NewsletterSubscription, Long> {

    boolean existsByEmailIgnoreCase(String email);
}
