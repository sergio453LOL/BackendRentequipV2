package com.rentequip.backend.controllers;

import com.rentequip.backend.dtos.request.NewsletterSubscriptionRequest;
import com.rentequip.backend.dtos.response.NewsletterSubscriptionResponse;
import com.rentequip.backend.services.NewsletterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: visitors of the landing page sign up for offers without an account. */
@RestController
@RequestMapping("/api/v1/newsletter/subscriptions")
@RequiredArgsConstructor
public class NewsletterController {

    private final NewsletterService newsletterService;

    @PostMapping
    public ResponseEntity<NewsletterSubscriptionResponse> subscribe(@Valid @RequestBody NewsletterSubscriptionRequest request) {
        NewsletterSubscriptionResponse response = newsletterService.subscribe(request);
        return ResponseEntity.status(response.alreadySubscribed() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}
