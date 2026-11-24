package com.rentequip.backend.dtos.response;

/**
 * @param alreadySubscribed the address was signed up before; nothing was sent again
 */
public record NewsletterSubscriptionResponse(String email, boolean alreadySubscribed) {
}
